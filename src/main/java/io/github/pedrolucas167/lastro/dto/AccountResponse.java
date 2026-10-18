package io.github.pedrolucas167.lastro.dto;

import io.github.pedrolucas167.lastro.domain.Account;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class AccountResponse {
    private String id;
    private BigDecimal balance;
    /** A identidade do Keycloak que responde pela conta; nulo enquanto ninguem a tiver. */
    private String holder;

    public static AccountResponse of(Account account) {
        return new AccountResponse(account.getId(), account.getBalance(), account.getHolder());
    }
}
