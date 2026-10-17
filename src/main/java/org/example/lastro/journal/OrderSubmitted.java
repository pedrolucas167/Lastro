package org.example.lastro.journal;

import org.example.lastro.domain.OrderSide;
import org.example.lastro.domain.OrderType;
import org.example.lastro.domain.TimeInForce;

import java.math.BigDecimal;
import java.time.Instant;

/** Comando: uma ordem foi criada e enviada ao engine. */
public record OrderSubmitted(String orderId, String symbol, OrderSide side, OrderType orderType,
                             BigDecimal price, int quantity, String accountId,
                             TimeInForce timeInForce, Instant occurredAt)
        implements TradingEvent {

    @Override
    public String type() {
        return "OrderSubmitted";
    }
}
