package io.github.pedrolucas167.lastro.journal;

import io.github.pedrolucas167.lastro.domain.Actor;

import java.math.BigDecimal;
import java.time.Instant;

public record FundsCredited(String accountId, BigDecimal amount, Actor actor, Instant occurredAt)
        implements MoneyEvent {

    @Override
    public String type() {
        return "FundsCredited";
    }
}
