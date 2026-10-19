package io.github.pedrolucas167.lastro.exception;

public class TransferIdempotencyKeyReusedException extends RuntimeException {
    public TransferIdempotencyKeyReusedException(String transactionId) {
        super("Idempotency key already used for another transfer: " + transactionId);
    }
}
