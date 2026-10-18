package io.github.pedrolucas167.lastro.journal;

import java.time.Instant;

public record AccountHolderAssigned(String accountId, String holder, Instant occurredAt)
        implements MoneyEvent {

    @Override
    public String type() {
        return "AccountHolderAssigned";
    }
}
