package org.example.lastro.journal;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountOpened(String accountId, BigDecimal initialBalance, Instant occurredAt)
        implements DomainEvent {

    @Override
    public String type() {
        return "AccountOpened";
    }
}
