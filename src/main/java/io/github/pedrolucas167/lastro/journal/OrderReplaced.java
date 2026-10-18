package io.github.pedrolucas167.lastro.journal;

import io.github.pedrolucas167.lastro.domain.Actor;

import java.math.BigDecimal;
import java.time.Instant;

/** Comando: preco/quantidade de uma ordem em repouso foram substituidos. */
public record OrderReplaced(String orderId, String symbol, BigDecimal price, BigDecimal quantity,
                            Actor actor, Instant occurredAt)
        implements TradingEvent {

    @Override
    public String type() {
        return "OrderReplaced";
    }
}
