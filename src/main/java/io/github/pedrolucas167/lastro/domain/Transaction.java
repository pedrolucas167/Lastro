package io.github.pedrolucas167.lastro.domain;

import java.math.BigDecimal;
import java.time.Instant;

public class Transaction {
    private final String id;
    private final String fromAccountId;
    private final String toAccountId;
    private final BigDecimal amount;
    private final TransactionType type;
    private final Instant timestamp;
    private TransactionStatus status;

    public Transaction(String id, String fromAccountId, String toAccountId, 
                      BigDecimal amount, TransactionType type) {
        this.id = id;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.type = type;
        this.timestamp = Instant.now();
        this.status = TransactionStatus.PENDING;
    }

    public String getId() {
        return id;
    }

    public String getFromAccountId() {
        return fromAccountId;
    }

    public String getToAccountId() {
        return toAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionType getType() {
        return type;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }
}
