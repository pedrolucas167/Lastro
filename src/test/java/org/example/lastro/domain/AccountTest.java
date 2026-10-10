package org.example.lastro.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AccountTest {
    private Account account;

    @BeforeEach
    void setUp() {
        account = new Account("acc-1", new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("Deve criar conta com saldo inicial correto")
    void shouldCreateAccountWithInitialBalance() {
        assertEquals("acc-1", account.getId());
        assertEquals(new BigDecimal("1000.00"), account.getBalance());
    }

    @Test
    @DisplayName("Deve creditar valor corretamente")
    void shouldCreditAmount() {
        account.credit(new BigDecimal("500.00"));
        assertEquals(new BigDecimal("1500.00"), account.getBalance());
    }

    @Test
    @DisplayName("Deve debitar valor quando saldo suficiente")
    void shouldDebitAmountWhenSufficientBalance() {
        account.debit(new BigDecimal("300.00"));
        assertEquals(new BigDecimal("700.00"), account.getBalance());
    }

    @Test
    @DisplayName("Deve lançar exceção ao debitar com saldo insuficiente")
    void shouldThrowExceptionWhenDebitingInsufficientBalance() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> account.debit(new BigDecimal("1500.00"))
        );
        assertEquals("Insufficient funds", exception.getMessage());
        assertEquals(new BigDecimal("1000.00"), account.getBalance());
    }

    @Test
    @DisplayName("Deve retornar true ao tentar debitar com saldo suficiente")
    void shouldReturnTrueWhenTryDebitWithSufficientBalance() {
        assertTrue(account.tryDebit(new BigDecimal("300.00")));
        assertEquals(new BigDecimal("700.00"), account.getBalance());
    }

    @Test
    @DisplayName("Deve retornar false ao tentar debitar com saldo insuficiente")
    void shouldReturnFalseWhenTryDebitWithInsufficientBalance() {
        assertFalse(account.tryDebit(new BigDecimal("1500.00")));
        assertEquals(new BigDecimal("1000.00"), account.getBalance());
    }

    @Test
    @DisplayName("Deve lidar com valores decimais corretamente")
    void shouldHandleDecimalValues() {
        account.credit(new BigDecimal("0.50"));
        account.debit(new BigDecimal("0.25"));
        assertEquals(new BigDecimal("1000.25"), account.getBalance());
    }

    @Test
    @DisplayName("Deve permitir debitar exatamente o saldo disponível")
    void shouldAllowDebitingExactBalance() {
        account.debit(new BigDecimal("1000.00"));
        assertEquals(BigDecimal.ZERO, account.getBalance());
    }

    @Test
    @DisplayName("Deve lançar exceção para valores negativos no crédito")
    void shouldThrowExceptionForNegativeCredit() {
        assertThrows(
            ArithmeticException.class,
            () -> account.credit(new BigDecimal("-100.00"))
        );
    }

    @Test
    @DisplayName("Deve lançar exceção para valores negativos no débito")
    void shouldThrowExceptionForNegativeDebit() {
        assertThrows(
            ArithmeticException.class,
            () -> account.debit(new BigDecimal("-100.00"))
        );
    }
}
