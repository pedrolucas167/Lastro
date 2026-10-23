package io.github.pedrolucas167.lastro.exception;

/**
 * Cliente suspenso tentou operar. A recusa e 403 como a de lastro: o pedido nao vira fato, mas o
 * cliente continua dono do que tem.
 */
public class ClientSuspendedException extends RuntimeException {
    public ClientSuspendedException(String clientId, String reason) {
        super(reason == null || reason.isBlank()
            ? "Client " + clientId + " is suspended"
            : "Client " + clientId + " is suspended: " + reason);
    }
}
