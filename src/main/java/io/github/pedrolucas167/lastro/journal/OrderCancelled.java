package io.github.pedrolucas167.lastro.journal;

import io.github.pedrolucas167.lastro.domain.Actor;

import java.time.Instant;

/** Comando: uma ordem foi cancelada. */
public record OrderCancelled(String orderId, String symbol, Actor actor, Instant occurredAt)
        implements TradingEvent {

    @Override
    public String type() {
        return "OrderCancelled";
    }
}
