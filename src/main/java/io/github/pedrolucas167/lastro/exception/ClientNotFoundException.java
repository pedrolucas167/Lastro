package io.github.pedrolucas167.lastro.exception;

public class ClientNotFoundException extends RuntimeException {
    public ClientNotFoundException(String clientIdOrSubject) {
        super("Client not found: " + clientIdOrSubject);
    }
}
