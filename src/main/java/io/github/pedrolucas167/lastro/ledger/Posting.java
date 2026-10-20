package io.github.pedrolucas167.lastro.ledger;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Uma partida: um valor que entra ou sai de uma conta do plano. O valor e sempre positivo -- o lado
 * diz o sentido, e nao o sinal.
 */
public record Posting(String accountCode, LedgerSide side, BigDecimal amount) {

    public Posting {
        if (accountCode == null || accountCode.isBlank()) {
            throw new IllegalArgumentException("Posting needs an account code");
        }
        Objects.requireNonNull(side, "side");
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Posting amount must be positive: " + amount);
        }
    }
}
