package org.example.lastro.journal;

import java.math.BigDecimal;
import java.time.Instant;

public record FundsDebited(String accountId, BigDecimal amount, Instant occurredAt)
        implements DomainEvent {

    @Override
    public String type() {
        return "FundsDebited";
    }
}
