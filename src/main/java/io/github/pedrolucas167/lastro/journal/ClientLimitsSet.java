package io.github.pedrolucas167.lastro.journal;

import io.github.pedrolucas167.lastro.domain.Actor;

import java.math.BigDecimal;
import java.time.Instant;

/** O operador definiu os limites do cliente. Cada definicao e um fato; o replay fica com a ultima. */
public record ClientLimitsSet(String clientId, BigDecimal orderNotional, BigDecimal dailyTransfer,
                              Actor actor, Instant occurredAt) implements ClientEvent {

    @Override
    public String type() {
        return "ClientLimitsSet";
    }
}
