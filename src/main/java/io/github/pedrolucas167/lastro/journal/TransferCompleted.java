package io.github.pedrolucas167.lastro.journal;

import io.github.pedrolucas167.lastro.domain.Actor;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferCompleted(String transactionId, String fromAccountId, String toAccountId,
                                BigDecimal amount, Actor actor, Instant occurredAt)
        implements MoneyEvent {

    @Override
    public String type() {
        return "TransferCompleted";
    }
}
