package io.github.pedrolucas167.lastro.exception;

public class TransactionFailedException extends RuntimeException {
    public TransactionFailedException(String reason) {
        super("Transaction failed: " + reason);
    }
}
