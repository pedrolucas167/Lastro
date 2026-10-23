package io.github.pedrolucas167.lastro.exception;

public class ClientAccessDeniedException extends RuntimeException {
    public ClientAccessDeniedException(String message) {
        super(message);
    }
}
