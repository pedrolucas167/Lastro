package io.github.pedrolucas167.lastro.exception;

public class OrderIdempotencyKeyReusedException extends RuntimeException {
    public OrderIdempotencyKeyReusedException(String orderId) {
        super("Idempotency key already used for another order: " + orderId);
    }
}
