package io.github.pedrolucas167.lastro.journal;

import io.github.pedrolucas167.lastro.domain.Actor;

import java.time.Instant;

public record AccountHolderAssigned(String accountId, String holder, Actor actor,
                                    Instant occurredAt)
        implements MoneyEvent {

    @Override
    public String type() {
        return "AccountHolderAssigned";
    }
}
