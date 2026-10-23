package io.github.pedrolucas167.lastro.journal;

import io.github.pedrolucas167.lastro.domain.Actor;

import java.time.Instant;

/** O operador suspendeu o cliente: ele nao abre ordem nem tira dinheiro ate ser reativado. */
public record ClientSuspended(String clientId, String reason, Actor actor, Instant occurredAt)
        implements ClientEvent {

    @Override
    public String type() {
        return "ClientSuspended";
    }
}
