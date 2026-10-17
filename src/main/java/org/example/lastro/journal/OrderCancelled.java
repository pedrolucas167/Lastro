package org.example.lastro.journal;

import java.time.Instant;

/** Comando: uma ordem foi cancelada. */
public record OrderCancelled(String orderId, String symbol, Instant occurredAt)
        implements TradingEvent {

    @Override
    public String type() {
        return "OrderCancelled";
    }
}
