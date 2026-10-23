package io.github.pedrolucas167.lastro.exception;

/** O cliente tentou operar acima do limite que o operador definiu. Nada foi movido nem gravado. */
public class ClientLimitExceededException extends RuntimeException {
    public ClientLimitExceededException(String message) {
        super(message);
    }
}
