package io.github.pedrolucas167.lastro.api;

import jakarta.validation.Valid;
import io.github.pedrolucas167.lastro.domain.Account;
import io.github.pedrolucas167.lastro.dto.AccountResponse;
import io.github.pedrolucas167.lastro.dto.AmountRequest;
import io.github.pedrolucas167.lastro.dto.CreateAccountRequest;
import io.github.pedrolucas167.lastro.service.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        Account account = accountService.createAccount(request.getInitialBalance());
        return ResponseEntity.ok(new AccountResponse(account.getId(), account.getBalance()));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable String accountId) {
        Account account = accountService.getAccount(accountId);
        if (account == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new AccountResponse(account.getId(), account.getBalance()));
    }

    @PostMapping("/{accountId}/credit")
    public ResponseEntity<AccountResponse> credit(@PathVariable String accountId,
                                                   @Valid @RequestBody AmountRequest request) {
        Account account = accountService.credit(accountId, request.getAmount());
        return ResponseEntity.ok(new AccountResponse(account.getId(), account.getBalance()));
    }

    @PostMapping("/{accountId}/debit")
    public ResponseEntity<AccountResponse> debit(@PathVariable String accountId,
                                                  @Valid @RequestBody AmountRequest request) {
        Account account = accountService.debit(accountId, request.getAmount());
        return ResponseEntity.ok(new AccountResponse(account.getId(), account.getBalance()));
    }
}
