package org.example.lastro.exception;

public class TransactionFailedException extends RuntimeException {
    public TransactionFailedException(String reason) {
        super("Transaction failed: " + reason);
    }
}
