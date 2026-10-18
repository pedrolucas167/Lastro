package io.github.pedrolucas167.lastro.exception;

/** O ator nao e o titular do recurso, e nao e back office. */
public class AccountAccessDeniedException extends RuntimeException {
    public AccountAccessDeniedException(String message) {
        super(message);
    }
}
