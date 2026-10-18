package io.github.pedrolucas167.lastro.journal;

import io.github.pedrolucas167.lastro.domain.Actor;
import io.github.pedrolucas167.lastro.domain.OrderSide;
import io.github.pedrolucas167.lastro.domain.OrderType;
import io.github.pedrolucas167.lastro.domain.TimeInForce;

import java.math.BigDecimal;
import java.time.Instant;

/** Comando: uma ordem foi criada e enviada ao engine. */
public record OrderSubmitted(String orderId, String symbol, OrderSide side, OrderType orderType,
                             BigDecimal price, int quantity, String accountId,
                             TimeInForce timeInForce, Actor actor, Instant occurredAt)
        implements TradingEvent {

    @Override
    public String type() {
        return "OrderSubmitted";
    }
}
