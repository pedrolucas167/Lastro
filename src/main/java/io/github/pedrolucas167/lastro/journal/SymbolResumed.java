package io.github.pedrolucas167.lastro.journal;

import java.time.Instant;

/** Comando: o trading de um simbolo foi retomado. */
public record SymbolResumed(String symbol, Instant occurredAt)
        implements MarketEvent {

    @Override
    public String type() {
        return "SymbolResumed";
    }
}
