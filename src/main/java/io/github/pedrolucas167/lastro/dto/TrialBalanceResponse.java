package io.github.pedrolucas167.lastro.dto;

import io.github.pedrolucas167.lastro.ledger.LedgerAccount;
import io.github.pedrolucas167.lastro.ledger.LedgerService;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

/** O balanco do livro: de um lado os debitos, do outro os creditos, e a prova de que fecham. */
@Data
@AllArgsConstructor
public class TrialBalanceResponse {
    /** Todo debito tem credito: os dois lados do livro somam o mesmo. */
    private boolean balanced;
    private BigDecimal totalDebits;
    private BigDecimal totalCredits;
    /** O que a corretora deve aos clientes: a soma dos saldos deles. */
    private BigDecimal clientLiabilities;
    /** O caixa na liquidante, que responde por esses passivos. */
    private BigDecimal settlement;
    private List<Account> accounts;

    public static TrialBalanceResponse of(LedgerService ledger) {
        List<LedgerAccount> accounts = ledger.trialBalance();
        return new TrialBalanceResponse(ledger.isBalanced(),
            total(accounts, LedgerAccount::getDebits),
            total(accounts, LedgerAccount::getCredits),
            ledger.clientLiabilitiesTotal(),
            ledger.settlementBalance(),
            accounts.stream().map(Account::of).toList());
    }

    private static BigDecimal total(List<LedgerAccount> accounts,
                                    Function<LedgerAccount, BigDecimal> value) {
        return accounts.stream().map(value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Uma conta do plano: natureza, os dois lados e o saldo pela natureza dela. */
    @Data
    @AllArgsConstructor
    public static class Account {
        private String code;
        private String nature;
        private BigDecimal debits;
        private BigDecimal credits;
        private BigDecimal balance;

        static Account of(LedgerAccount account) {
            return new Account(account.getCode(), account.getNature().name(), account.getDebits(),
                account.getCredits(), account.getBalance());
        }
    }
}
