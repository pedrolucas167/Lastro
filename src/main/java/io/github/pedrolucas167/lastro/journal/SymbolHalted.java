package io.github.pedrolucas167.lastro.journal;

import io.github.pedrolucas167.lastro.domain.Actor;

import java.time.Instant;

/** Comando: o trading de um simbolo foi pausado, com o motivo registrado. */
public record SymbolHalted(String symbol, String reason, Actor actor, Instant occurredAt)
        implements MarketEvent {

    @Override
    public String type() {
        return "SymbolHalted";
    }
}
