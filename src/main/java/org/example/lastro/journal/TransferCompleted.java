package org.example.lastro.journal;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferCompleted(String transactionId, String fromAccountId, String toAccountId,
                                BigDecimal amount, Instant occurredAt)
        implements DomainEvent {

    @Override
    public String type() {
        return "TransferCompleted";
    }
}
