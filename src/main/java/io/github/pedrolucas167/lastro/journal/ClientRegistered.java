package io.github.pedrolucas167.lastro.journal;

import io.github.pedrolucas167.lastro.domain.Actor;

import java.time.Instant;

/** O titular ganhou cadastro de cliente: nome, status e, depois, limites. */
public record ClientRegistered(String clientId, String subject, String name, Actor actor,
                               Instant occurredAt) implements ClientEvent {

    @Override
    public String type() {
        return "ClientRegistered";
    }
}
