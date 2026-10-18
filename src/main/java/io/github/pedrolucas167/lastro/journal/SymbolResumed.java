package io.github.pedrolucas167.lastro.journal;

import io.github.pedrolucas167.lastro.domain.Actor;

import java.time.Instant;

/** Comando: o trading de um simbolo foi retomado. */
public record SymbolResumed(String symbol, Actor actor, Instant occurredAt)
        implements MarketEvent {

    @Override
    public String type() {
        return "SymbolResumed";
    }
}
