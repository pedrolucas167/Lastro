package io.github.pedrolucas167.lastro.domain;

import java.time.Instant;

/**
 * O cliente do titular. O vinculo com as contas continua sendo o `sub` do token -- o cliente e o
 * cadastro que da nome, status e (depois) limites a esse `sub`.
 *
 * Nome e subject nao mudam: o cadastro nasce uma vez e o que muda de estado (suspenso, ativo) e o
 * que vira fato novo no journal.
 */
public class Client {
    private final String id;
    private final String subject;
    private final String name;
    private final Instant registeredAt;
    private volatile ClientStatus status = ClientStatus.ACTIVE;
    /** Por que o operador suspendeu: o cliente le na consulta e o suporte sabe o que responder. */
    private volatile String suspensionReason;
    private volatile ClientLimits limits = ClientLimits.NONE;

    public Client(String id, String subject, String name, Instant registeredAt) {
        this.id = id;
        this.subject = subject;
        this.name = name;
        this.registeredAt = registeredAt;
    }

    public String getId() {
        return id;
    }

    public String getSubject() {
        return subject;
    }

    public String getName() {
        return name;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public ClientStatus getStatus() {
        return status;
    }

    public String getSuspensionReason() {
        return suspensionReason;
    }

    public ClientLimits getLimits() {
        return limits;
    }

    public void setLimits(ClientLimits limits) {
        this.limits = limits;
    }

    public boolean active() {
        return status == ClientStatus.ACTIVE;
    }

    public void suspend(String reason) {
        this.status = ClientStatus.SUSPENDED;
        this.suspensionReason = reason;
    }

    public void reactivate() {
        this.status = ClientStatus.ACTIVE;
        this.suspensionReason = null;
    }
}
