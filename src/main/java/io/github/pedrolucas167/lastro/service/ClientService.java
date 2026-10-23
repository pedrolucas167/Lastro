package io.github.pedrolucas167.lastro.service;

import io.github.pedrolucas167.lastro.domain.Actor;
import io.github.pedrolucas167.lastro.domain.Client;
import io.github.pedrolucas167.lastro.exception.ClientAccessDeniedException;
import io.github.pedrolucas167.lastro.exception.ClientAlreadyRegisteredException;
import io.github.pedrolucas167.lastro.exception.ClientNotFoundException;
import io.github.pedrolucas167.lastro.exception.ClientSuspendedException;
import io.github.pedrolucas167.lastro.journal.ClientReactivated;
import io.github.pedrolucas167.lastro.journal.ClientRegistered;
import io.github.pedrolucas167.lastro.journal.ClientSuspended;
import io.github.pedrolucas167.lastro.journal.DomainEvent;
import io.github.pedrolucas167.lastro.journal.EventStore;
import io.github.pedrolucas167.lastro.journal.JournalRebuildable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * O cliente do titular: quem ele e e se pode operar. O vinculo com as contas continua sendo o `sub`
 * do token, entao nenhuma conta muda de forma -- o cliente e o cadastro em volta desse `sub`.
 *
 * Titular sem cadastro continua operando: o journal de 0.1-0.6 nao tinha cliente, e o passado nao
 * e reescrito. Suspender so vale para quem tem cadastro, e a suspensao e um fato no journal como
 * qualquer outro -- o replay devolve o cliente suspenso.
 */
@Service
@Order(0)
public class ClientService implements JournalRebuildable {
    private static final Logger logger = LoggerFactory.getLogger(ClientService.class);

    private final EventStore eventStore;
    private final Map<String, Client> clientsById = new ConcurrentHashMap<>();
    private final Map<String, Client> clientsBySubject = new ConcurrentHashMap<>();

    public ClientService(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    /** Registra o cliente de um titular. Um cliente por `sub`, e so o operador registra. */
    public Client register(Actor actor, String subject, String name) {
        requireOperator(actor);
        if (isBlank(subject) || isBlank(name)) {
            throw new IllegalArgumentException("subject and name are required");
        }

        String clientId = UUID.randomUUID().toString();
        Instant at = Instant.now();
        Client client = new Client(clientId, subject, name, at);
        // A unicidade por subject e o proprio putIfAbsent: dois registros do mesmo titular nao
        // passam, mesmo chegando juntos.
        if (clientsBySubject.putIfAbsent(subject, client) != null) {
            throw new ClientAlreadyRegisteredException(subject);
        }
        clientsById.put(clientId, client);
        try {
            eventStore.append(new ClientRegistered(clientId, subject, name, actor, at));
        } catch (RuntimeException e) {
            clientsBySubject.remove(subject, client);
            clientsById.remove(clientId, client);
            throw e;
        }
        logger.info("Registered client {} for subject {}", clientId, subject);
        return client;
    }

    public Client get(String clientId) {
        Client client = clientsById.get(clientId);
        if (client == null) {
            logger.warn("Client not found: {}", clientId);
            throw new ClientNotFoundException(clientId);
        }
        return client;
    }

    /** O cliente do `sub` do token, quando ha um registrado. */
    public Optional<Client> bySubject(String subject) {
        return Optional.ofNullable(subject == null ? null : clientsBySubject.get(subject));
    }

    /** Todos os clientes, em ordem estavel de nome e id. */
    public List<Client> all() {
        return clientsById.values().stream()
            .sorted(Comparator.comparing(Client::getName).thenComparing(Client::getId))
            .toList();
    }

    /** Suspender quem ja esta suspenso nao e fato novo: a resposta e o estado que ja vale. */
    public Client suspend(Actor actor, String clientId, String reason) {
        requireOperator(actor);
        Client client = get(clientId);
        if (!client.active()) {
            return client;
        }
        eventStore.append(new ClientSuspended(clientId, reason, actor, Instant.now()));
        client.suspend(reason);
        logger.info("Suspended client {}", clientId);
        return client;
    }

    public Client reactivate(Actor actor, String clientId) {
        requireOperator(actor);
        Client client = get(clientId);
        if (client.active()) {
            return client;
        }
        eventStore.append(new ClientReactivated(clientId, actor, Instant.now()));
        client.reactivate();
        logger.info("Reactivated client {}", clientId);
        return client;
    }

    /**
     * Cliente suspenso nao abre ordem nem tira dinheiro; o que entra, entra. Sem cadastro de
     * cliente nao ha o que barrar.
     */
    public void ensureMayOperate(String subject) {
        bySubject(subject).filter(client -> !client.active()).ifPresent(client -> {
            throw new ClientSuspendedException(client.getId(), client.getSuspensionReason());
        });
    }

    /** Reconstroi os clientes a partir do journal, na ordem dos fatos. */
    @Override
    public void rebuild(List<DomainEvent> events) {
        clientsById.clear();
        clientsBySubject.clear();
        for (DomainEvent event : events) {
            switch (event) {
                case ClientRegistered e -> {
                    Client client = new Client(e.clientId(), e.subject(), e.name(), e.occurredAt());
                    clientsById.put(e.clientId(), client);
                    clientsBySubject.put(e.subject(), client);
                }
                case ClientSuspended e -> {
                    Client client = clientsById.get(e.clientId());
                    if (client == null) {
                        logger.warn("Client {} was suspended before being registered: skipping",
                            e.clientId());
                        continue;
                    }
                    client.suspend(e.reason());
                }
                case ClientReactivated e -> {
                    Client client = clientsById.get(e.clientId());
                    if (client == null) {
                        logger.warn("Client {} was reactivated before being registered: skipping",
                            e.clientId());
                        continue;
                    }
                    client.reactivate();
                }
                default -> {
                }
            }
        }
        if (!clientsById.isEmpty()) {
            logger.info("Rebuilt {} clients from the journal", clientsById.size());
        }
    }

    private static void requireOperator(Actor actor) {
        if (!actor.privileged()) {
            throw new ClientAccessDeniedException("Only a privileged caller can manage clients");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
