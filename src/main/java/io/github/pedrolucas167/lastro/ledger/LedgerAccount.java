package io.github.pedrolucas167.lastro.ledger;

import java.math.BigDecimal;

/**
 * Os totais de uma conta do plano. O saldo e o que sobra do lado da natureza: ativo e
 * debitos - creditos, passivo e creditos - debitos.
 */
public final class LedgerAccount {

    private final String code;
    private final AccountNature nature;
    private BigDecimal debits = BigDecimal.ZERO;
    private BigDecimal credits = BigDecimal.ZERO;

    LedgerAccount(String code) {
        this.code = code;
        this.nature = ChartOfAccounts.natureOf(code);
    }

    synchronized void post(LedgerSide side, BigDecimal amount) {
        if (side == LedgerSide.DEBIT) {
            debits = debits.add(amount);
        } else {
            credits = credits.add(amount);
        }
    }

    public String getCode() {
        return code;
    }

    public AccountNature getNature() {
        return nature;
    }

    public synchronized BigDecimal getDebits() {
        return debits;
    }

    public synchronized BigDecimal getCredits() {
        return credits;
    }

    public synchronized BigDecimal getBalance() {
        return nature == AccountNature.ASSET ? debits.subtract(credits) : credits.subtract(debits);
    }

    @Override
    public String toString() {
        return code + "(D " + getDebits() + ", C " + getCredits() + ", saldo " + getBalance() + ")";
    }
}
