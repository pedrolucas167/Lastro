package io.github.pedrolucas167.lastro.journal;

import io.github.pedrolucas167.lastro.domain.Actor;

import java.time.Instant;

/** O operador devolveu o cliente a ativo. */
public record ClientReactivated(String clientId, Actor actor, Instant occurredAt)
        implements ClientEvent {

    @Override
    public String type() {
        return "ClientReactivated";
    }
}
