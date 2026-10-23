package io.github.pedrolucas167.lastro.service;

import io.github.pedrolucas167.lastro.domain.Actor;
import io.github.pedrolucas167.lastro.domain.Client;
import io.github.pedrolucas167.lastro.domain.ClientStatus;
import io.github.pedrolucas167.lastro.exception.ClientAccessDeniedException;
import io.github.pedrolucas167.lastro.exception.ClientAlreadyRegisteredException;
import io.github.pedrolucas167.lastro.exception.ClientNotFoundException;
import io.github.pedrolucas167.lastro.exception.ClientSuspendedException;
import io.github.pedrolucas167.lastro.journal.ClientReactivated;
import io.github.pedrolucas167.lastro.journal.ClientRegistered;
import io.github.pedrolucas167.lastro.journal.ClientSuspended;
import io.github.pedrolucas167.lastro.journal.DomainEvent;
import io.github.pedrolucas167.lastro.journal.EventStore;
import io.github.pedrolucas167.lastro.journal.InMemoryEventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientServiceTest {
    private static final Actor BACK_OFFICE = new Actor("operador", Set.of(Actor.OPERATOR));
    private static final Actor TITULAR = new Actor("titular-1", Set.of(Actor.HOLDER));
    private static final String SUBJECT = "titular-1";

    private EventStore eventStore;
    private ClientService clients;

    @BeforeEach
    void setUp() {
        eventStore = new InMemoryEventStore();
        clients = new ClientService(eventStore);
    }

    @Test
    @DisplayName("Registrar grava o fato e o cliente fica ativo, encontravel por id e por subject")
    void registerJournalsTheFact() {
        Client client = clients.register(BACK_OFFICE, SUBJECT, "Maria");

        assertEquals("Maria", client.getName());
        assertEquals(SUBJECT, client.getSubject());
        assertEquals(ClientStatus.ACTIVE, client.getStatus());
        assertTrue(client.active());
        assertEquals(client, clients.get(client.getId()));
        assertEquals(client, clients.bySubject(SUBJECT).orElseThrow());

        List<DomainEvent> events = eventStore.readAll();
        assertEquals(1, events.size());
        ClientRegistered fact = assertInstanceOf(ClientRegistered.class, events.getFirst());
        assertEquals(client.getId(), fact.clientId());
        assertEquals(SUBJECT, fact.subject());
        assertEquals(BACK_OFFICE, fact.actor());
    }

    @Test
    @DisplayName("Dois registros para o mesmo titular respondem conflito e um fato so")
    void registeringTheSameSubjectTwiceIsAConflict() {
        clients.register(BACK_OFFICE, SUBJECT, "Maria");

        assertThrows(ClientAlreadyRegisteredException.class,
            () -> clients.register(BACK_OFFICE, SUBJECT, "Maria Silva"));

        assertEquals(1, eventStore.readAll().size());
    }

    @Test
    @DisplayName("Titular nao registra cliente: o cadastro e ato de operador")
    void holderCannotRegister() {
        assertThrows(ClientAccessDeniedException.class,
            () -> clients.register(TITULAR, SUBJECT, "Maria"));

        assertTrue(eventStore.readAll().isEmpty());
    }

    @Test
    @DisplayName("Suspender grava o fato, barra a operacao e guarda o motivo")
    void suspendBlocksOperation() {
        Client client = clients.register(BACK_OFFICE, SUBJECT, "Maria");

        clients.suspend(BACK_OFFICE, client.getId(), "documentacao vencida");

        assertEquals(ClientStatus.SUSPENDED, client.getStatus());
        assertEquals("documentacao vencida", client.getSuspensionReason());
        ClientSuspendedException refused = assertThrows(ClientSuspendedException.class,
            () -> clients.ensureMayOperate(SUBJECT));
        assertTrue(refused.getMessage().contains("documentacao vencida"));

        List<DomainEvent> events = eventStore.readAll();
        assertEquals(2, events.size());
        assertEquals(new ClientSuspended(client.getId(), "documentacao vencida", BACK_OFFICE,
            events.get(1).occurredAt()), events.get(1));
    }

    @Test
    @DisplayName("Suspender duas vezes grava um fato; reativar devolve a operacao e grava outro")
    void suspendIsIdempotentAndReactivateRestores() {
        Client client = clients.register(BACK_OFFICE, SUBJECT, "Maria");

        clients.suspend(BACK_OFFICE, client.getId(), "revisao");
        clients.suspend(BACK_OFFICE, client.getId(), "revisao");
        assertEquals(2, eventStore.readAll().size());

        clients.reactivate(BACK_OFFICE, client.getId());
        clients.reactivate(BACK_OFFICE, client.getId());

        assertEquals(3, eventStore.readAll().size());
        assertTrue(client.active());
        assertNull(client.getSuspensionReason());
        clients.ensureMayOperate(SUBJECT);
        assertInstanceOf(ClientReactivated.class, eventStore.readAll().getLast());
    }

    @Test
    @DisplayName("Reativar quem ja esta ativo nao e fato novo")
    void reactivatingAnActiveClientWritesNothing() {
        Client client = clients.register(BACK_OFFICE, SUBJECT, "Maria");

        clients.reactivate(BACK_OFFICE, client.getId());

        assertEquals(1, eventStore.readAll().size());
    }

    @Test
    @DisplayName("Titular sem cadastro de cliente continua operando")
    void holderWithoutClientKeepsOperating() {
        clients.ensureMayOperate("sem-cadastro");
        clients.ensureMayOperate(null);
    }

    @Test
    @DisplayName("Cliente desconhecido responde nao encontrado")
    void unknownClientIsNotFound() {
        assertThrows(ClientNotFoundException.class, () -> clients.get("cliente-inexistente"));
        assertTrue(clients.bySubject("sem-cadastro").isEmpty());
    }

    @Test
    @DisplayName("O replay devolve o cliente suspenso, com o motivo")
    void replayRebuildsTheSuspendedClient() {
        Client client = clients.register(BACK_OFFICE, SUBJECT, "Maria");
        clients.suspend(BACK_OFFICE, client.getId(), "documentacao vencida");

        ClientService replayed = new ClientService(eventStore);
        replayed.rebuild(eventStore.readAll());

        Client rebuilt = replayed.bySubject(SUBJECT).orElseThrow();
        assertEquals(client.getId(), rebuilt.getId());
        assertEquals("Maria", rebuilt.getName());
        assertEquals(ClientStatus.SUSPENDED, rebuilt.getStatus());
        assertEquals("documentacao vencida", rebuilt.getSuspensionReason());
        assertThrows(ClientSuspendedException.class, () -> replayed.ensureMayOperate(SUBJECT));

        replayed.reactivate(BACK_OFFICE, rebuilt.getId());
        assertEquals(3, eventStore.readAll().size());
        replayed.ensureMayOperate(SUBJECT);
    }

    @Test
    @DisplayName("Listar devolve os clientes em ordem estavel de nome")
    void listsClientsByName() {
        clients.register(BACK_OFFICE, "subject-b", "Bruno");
        clients.register(BACK_OFFICE, "subject-a", "Ana");

        assertEquals(List.of("Ana", "Bruno"),
            clients.all().stream().map(Client::getName).toList());
    }
}
