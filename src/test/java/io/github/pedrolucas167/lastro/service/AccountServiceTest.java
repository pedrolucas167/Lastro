package io.github.pedrolucas167.lastro.service;

import io.github.pedrolucas167.lastro.domain.Account;
import io.github.pedrolucas167.lastro.domain.Actor;
import io.github.pedrolucas167.lastro.exception.AccountAccessDeniedException;
import io.github.pedrolucas167.lastro.exception.AccountAlreadyHasHolderException;
import io.github.pedrolucas167.lastro.exception.AccountNotFoundException;
import io.github.pedrolucas167.lastro.exception.InsufficientFundsException;
import io.github.pedrolucas167.lastro.journal.InMemoryEventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AccountServiceTest {
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(new InMemoryEventStore());
    }

    @Test
    @DisplayName("Deve criar conta com saldo inicial")
    void shouldCreateAccountWithInitialBalance() {
        Account account = accountService.createAccount(new BigDecimal("1000.00"));
        
        assertNotNull(account);
        assertNotNull(account.getId());
        assertEquals(new BigDecimal("1000.00"), account.getBalance());
    }

    @Test
    @DisplayName("Deve buscar conta existente")
    void shouldGetExistingAccount() {
        Account created = accountService.createAccount(new BigDecimal("500.00"));
        Account retrieved = accountService.getAccount(created.getId());
        
        assertNotNull(retrieved);
        assertEquals(created.getId(), retrieved.getId());
        assertEquals(created.getBalance(), retrieved.getBalance());
    }

    @Test
    @DisplayName("Deve lançar exceção ao buscar conta inexistente")
    void shouldThrowExceptionWhenGettingNonExistentAccount() {
        assertThrows(
            AccountNotFoundException.class,
            () -> accountService.getAccount("non-existent")
        );
    }

    @Test
    @DisplayName("Deve creditar valor em conta existente")
    void shouldCreditAmountToExistingAccount() {
        Account account = accountService.createAccount(new BigDecimal("1000.00"));
        Account updated = accountService.credit(account.getId(), new BigDecimal("500.00"));
        
        assertEquals(new BigDecimal("1500.00"), updated.getBalance());
    }

    @Test
    @DisplayName("Deve lançar exceção ao creditar conta inexistente")
    void shouldThrowExceptionWhenCreditingNonExistentAccount() {
        assertThrows(
            AccountNotFoundException.class,
            () -> accountService.credit("non-existent", new BigDecimal("100.00"))
        );
    }

    @Test
    @DisplayName("Deve debitar valor de conta existente com saldo suficiente")
    void shouldDebitAmountFromExistingAccountWithSufficientBalance() {
        Account account = accountService.createAccount(new BigDecimal("1000.00"));
        Account updated = accountService.debit(account.getId(), new BigDecimal("300.00"));
        
        assertEquals(new BigDecimal("700.00"), updated.getBalance());
    }

    @Test
    @DisplayName("Deve lançar exceção ao debitar conta inexistente")
    void shouldThrowExceptionWhenDebitingNonExistentAccount() {
        assertThrows(
            AccountNotFoundException.class,
            () -> accountService.debit("non-existent", new BigDecimal("100.00"))
        );
    }

    @Test
    @DisplayName("Deve lançar exceção ao debitar com saldo insuficiente")
    void shouldThrowExceptionWhenDebitingWithInsufficientBalance() {
        Account account = accountService.createAccount(new BigDecimal("100.00"));
        
        assertThrows(
            InsufficientFundsException.class,
            () -> accountService.debit(account.getId(), new BigDecimal("200.00"))
        );
    }

    @Test
    @DisplayName("Deve criar conta com titular")
    void shouldCreateAccountWithHolder() {
        Account account = accountService.createAccount(new BigDecimal("100.00"), "cliente-1");

        assertEquals("cliente-1", account.getHolder());
        assertTrue(account.heldBy("cliente-1"));
        assertFalse(account.heldBy("cliente-2"));
    }

    @Test
    @DisplayName("Deve dar titular a uma conta sem titular")
    void shouldAssignHolderToAccountWithoutOne() {
        Account account = accountService.createAccount(new BigDecimal("100.00"));

        Account assigned = accountService.assignHolder(account.getId(), "cliente-1");

        assertEquals("cliente-1", assigned.getHolder());
        assertTrue(accountService.accountsOf("cliente-1").stream().anyMatch(it -> it.getId().equals(account.getId())));
    }

    @Test
    @DisplayName("Não deve trocar o titular de uma conta que já tem")
    void shouldRefuseToReplaceHolder() {
        Account account = accountService.createAccount(new BigDecimal("100.00"), "cliente-1");

        assertThrows(
            AccountAlreadyHasHolderException.class,
            () -> accountService.assignHolder(account.getId(), "cliente-2")
        );
        assertEquals("cliente-1", accountService.getAccount(account.getId()).getHolder());
    }

    @Test
    @DisplayName("Deve lançar exceção ao dar titular a conta inexistente")
    void shouldThrowExceptionWhenAssigningHolderToNonExistentAccount() {
        assertThrows(
            AccountNotFoundException.class,
            () -> accountService.assignHolder("non-existent", "cliente-1")
        );
    }

    @Test
    @DisplayName("O titular só acessa a própria conta; o back office acessa qualquer uma")
    void shouldEnforceAccountAccess() {
        Account mine = accountService.createAccount(new BigDecimal("100.00"), "cliente-1");
        Account other = accountService.createAccount(new BigDecimal("100.00"), "cliente-2");
        Actor holder = new Actor("cliente-1", false);

        assertEquals(mine.getId(), accountService.getAccountFor(holder, mine.getId()).getId());
        assertTrue(accountService.canAccess(holder, mine.getId()));
        assertFalse(accountService.canAccess(holder, other.getId()));
        assertThrows(
            AccountAccessDeniedException.class,
            () -> accountService.getAccountFor(holder, other.getId())
        );

        Actor backOffice = new Actor("operador", true);
        assertEquals(other.getId(), accountService.getAccountFor(backOffice, other.getId()).getId());
    }

    @Test
    @DisplayName("Lista as contas do titular em ordem estável")
    void shouldListAccountsOfHolder() {
        Account second = accountService.createAccount(new BigDecimal("1.00"), "cliente-1");
        Account first = accountService.createAccount(new BigDecimal("2.00"), "cliente-1");
        accountService.createAccount(new BigDecimal("3.00"), "cliente-2");

        List<String> ids = accountService.accountsOf("cliente-1").stream().map(Account::getId).toList();

        assertEquals(List.of(first.getId(), second.getId()).stream().sorted().toList(), ids);
        assertTrue(accountService.accountsOf("cliente-9").isEmpty());
    }
}
