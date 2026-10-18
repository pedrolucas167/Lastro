package io.github.pedrolucas167.lastro.journal;

import io.github.pedrolucas167.lastro.domain.Actor;
import io.github.pedrolucas167.lastro.domain.OrderSide;
import io.github.pedrolucas167.lastro.domain.OrderType;
import io.github.pedrolucas167.lastro.domain.TimeInForce;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Comando: uma ordem foi criada e enviada ao engine.
 *
 * A chave de idempotencia do cliente entra no fato: e ela que diz, no replay, que um retry
 * com a mesma chave nao pode virar uma segunda ordem. Nula quando o cliente nao mandou chave.
 */
public record OrderSubmitted(String orderId, String symbol, OrderSide side, OrderType orderType,
                             BigDecimal price, int quantity, String accountId,
                             TimeInForce timeInForce, String idempotencyKey, Actor actor,
                             Instant occurredAt)
        implements TradingEvent {

    @Override
    public String type() {
        return "OrderSubmitted";
    }
}
