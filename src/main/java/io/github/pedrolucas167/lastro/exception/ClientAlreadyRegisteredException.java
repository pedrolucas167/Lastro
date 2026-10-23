package io.github.pedrolucas167.lastro.exception;

public class ClientAlreadyRegisteredException extends RuntimeException {
    public ClientAlreadyRegisteredException(String subject) {
        super("Client already registered for subject: " + subject);
    }
}
