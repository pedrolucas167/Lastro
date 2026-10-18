package io.github.pedrolucas167.lastro.api;

import jakarta.validation.Valid;
import io.github.pedrolucas167.lastro.config.Roles;
import io.github.pedrolucas167.lastro.domain.Account;
import io.github.pedrolucas167.lastro.domain.Actor;
import io.github.pedrolucas167.lastro.dto.AccountResponse;
import io.github.pedrolucas167.lastro.dto.AmountRequest;
import io.github.pedrolucas167.lastro.dto.AssignHolderRequest;
import io.github.pedrolucas167.lastro.dto.CreateAccountRequest;
import io.github.pedrolucas167.lastro.exception.AccountAccessDeniedException;
import io.github.pedrolucas167.lastro.service.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(Authentication authentication,
                                                        @Valid @RequestBody CreateAccountRequest request) {
        Actor actor = Roles.actor(authentication);
        Account account = accountService.createAccount(actor, request.getInitialBalance(), holderFor(actor, request));
        return ResponseEntity.ok(AccountResponse.of(account));
    }

    /** As contas que o proprio token tem. */
    @GetMapping("/mine")
    public ResponseEntity<List<AccountResponse>> myAccounts(Authentication authentication) {
        Actor actor = Roles.actor(authentication);
        List<AccountResponse> accounts = accountService.accountsOf(actor.subject()).stream()
            .map(AccountResponse::of)
            .toList();
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccount(Authentication authentication,
                                                     @PathVariable String accountId) {
        Account account = accountService.getAccountFor(Roles.actor(authentication), accountId);
        return ResponseEntity.ok(AccountResponse.of(account));
    }

    /** Da titular a uma conta que ainda nao tem. Ato de operador. */
    @PostMapping("/{accountId}/holder")
    public ResponseEntity<AccountResponse> assignHolder(Authentication authentication,
                                                       @PathVariable String accountId,
                                                       @Valid @RequestBody AssignHolderRequest request) {
        Account account = accountService.assignHolder(Roles.actor(authentication), accountId, request.getHolder());
        return ResponseEntity.ok(AccountResponse.of(account));
    }

    @PostMapping("/{accountId}/credit")
    public ResponseEntity<AccountResponse> credit(Authentication authentication,
                                                   @PathVariable String accountId,
                                                   @Valid @RequestBody AmountRequest request) {
        Account account = accountService.credit(Roles.actor(authentication), accountId, request.getAmount());
        return ResponseEntity.ok(AccountResponse.of(account));
    }

    @PostMapping("/{accountId}/debit")
    public ResponseEntity<AccountResponse> debit(Authentication authentication,
                                                  @PathVariable String accountId,
                                                  @Valid @RequestBody AmountRequest request) {
        Account account = accountService.debit(Roles.actor(authentication), accountId, request.getAmount());
        return ResponseEntity.ok(AccountResponse.of(account));
    }

    /**
     * O titular de uma conta nova: o que um privilegiado mandar abrir em nome de, ou o
     * proprio chamador -- que nao abre conta para outro.
     */
    private String holderFor(Actor actor, CreateAccountRequest request) {
        if (actor.privileged()) {
            return request.getHolder();
        }
        if (request.getHolder() != null && !request.getHolder().equals(actor.subject())) {
            throw new AccountAccessDeniedException("Only a privileged caller can open an account for another holder");
        }
        return actor.subject();
    }
}
