package io.github.pedrolucas167.lastro.exception;

public class AccountAlreadyHasHolderException extends RuntimeException {
    public AccountAlreadyHasHolderException(String accountId) {
        super("Account already has a holder: " + accountId);
    }
}
