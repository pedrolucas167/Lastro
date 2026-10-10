package org.example.lastro.service;

import org.example.lastro.domain.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AccountServiceTest {
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService();
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
    @DisplayName("Deve retornar null ao buscar conta inexistente")
    void shouldReturnNullForNonExistentAccount() {
        Account account = accountService.getAccount("non-existent");
        assertNull(account);
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
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> accountService.credit("non-existent", new BigDecimal("100.00"))
        );
        assertEquals("Account not found", exception.getMessage());
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
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> accountService.debit("non-existent", new BigDecimal("100.00"))
        );
        assertEquals("Account not found", exception.getMessage());
    }

    @Test
    @DisplayName("Deve lançar exceção ao debitar com saldo insuficiente")
    void shouldThrowExceptionWhenDebitingWithInsufficientBalance() {
        Account account = accountService.createAccount(new BigDecimal("100.00"));
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> accountService.debit(account.getId(), new BigDecimal("200.00"))
        );
        assertEquals("Insufficient funds", exception.getMessage());
    }
}
