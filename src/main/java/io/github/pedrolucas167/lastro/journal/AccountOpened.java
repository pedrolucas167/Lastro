package io.github.pedrolucas167.lastro.journal;

import io.github.pedrolucas167.lastro.domain.Actor;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountOpened(String accountId, BigDecimal initialBalance, Actor actor,
                            Instant occurredAt)
        implements MoneyEvent {

    @Override
    public String type() {
        return "AccountOpened";
    }
}
