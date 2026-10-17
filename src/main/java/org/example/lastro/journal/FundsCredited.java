package org.example.lastro.journal;

import java.math.BigDecimal;
import java.time.Instant;

public record FundsCredited(String accountId, BigDecimal amount, Instant occurredAt)
        implements MoneyEvent {

    @Override
    public String type() {
        return "FundsCredited";
    }
}
