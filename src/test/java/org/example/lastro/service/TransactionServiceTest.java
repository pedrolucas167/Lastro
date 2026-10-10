package org.example.lastro.service;

import org.example.lastro.domain.Account;
import org.example.lastro.domain.Transaction;
import org.example.lastro.domain.TransactionStatus;
import org.example.lastro.domain.TransactionType;
import org.example.lastro.exception.TransactionFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class TransactionServiceTest {
    private AccountService accountService;
    private TransactionService transactionService;
    private Account fromAccount;
    private Account toAccount;

    @BeforeEach
    void setUp() {
        accountService = new AccountService();
        IdempotencyService idempotencyService = new IdempotencyService();
        transactionService = new TransactionService(accountService, idempotencyService);
        
        fromAccount = accountService.createAccount(new BigDecimal("1000.00"));
        toAccount = accountService.createAccount(new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("Deve transferir com sucesso quando saldo suficiente")
    void shouldTransferSuccessfullyWhenSufficientBalance() {
        Transaction transaction = transactionService.transfer(
            fromAccount.getId(),
            toAccount.getId(),
            new BigDecimal("300.00")
        );
        
        assertNotNull(transaction);
        assertEquals(TransactionStatus.COMPLETED, transaction.getStatus());
        assertEquals(fromAccount.getId(), transaction.getFromAccountId());
        assertEquals(toAccount.getId(), transaction.getToAccountId());
        assertEquals(new BigDecimal("300.00"), transaction.getAmount());
        
        assertEquals(new BigDecimal("700.00"), accountService.getAccount(fromAccount.getId()).getBalance());
        assertEquals(new BigDecimal("800.00"), accountService.getAccount(toAccount.getId()).getBalance());
    }

    @Test
    @DisplayName("Deve falhar transferência quando conta origem não existe")
    void shouldFailTransferWhenFromAccountDoesNotExist() {
        assertThrows(
            TransactionFailedException.class,
            () -> transactionService.transfer(
                "non-existent",
                toAccount.getId(),
                new BigDecimal("100.00")
            )
        );
    }

    @Test
    @DisplayName("Deve falhar transferência quando conta destino não existe")
    void shouldFailTransferWhenToAccountDoesNotExist() {
        assertThrows(
            TransactionFailedException.class,
            () -> transactionService.transfer(
                fromAccount.getId(),
                "non-existent",
                new BigDecimal("100.00")
            )
        );
    }

    @Test
    @DisplayName("Deve falhar transferência quando saldo insuficiente")
    void shouldFailTransferWhenInsufficientBalance() {
        Transaction transaction = transactionService.transfer(
            fromAccount.getId(),
            toAccount.getId(),
            new BigDecimal("2000.00")
        );
        
        assertEquals(TransactionStatus.FAILED, transaction.getStatus());
        assertEquals(new BigDecimal("1000.00"), accountService.getAccount(fromAccount.getId()).getBalance());
        assertEquals(new BigDecimal("500.00"), accountService.getAccount(toAccount.getId()).getBalance());
    }

    @Test
    @DisplayName("Deve buscar transação existente")
    void shouldGetExistingTransaction() {
        Transaction created = transactionService.transfer(
            fromAccount.getId(),
            toAccount.getId(),
            new BigDecimal("100.00")
        );
        
        Transaction retrieved = transactionService.getTransaction(created.getId());
        
        assertNotNull(retrieved);
        assertEquals(created.getId(), retrieved.getId());
        assertEquals(created.getStatus(), retrieved.getStatus());
    }

    @Test
    @DisplayName("Deve retornar null ao buscar transação inexistente")
    void shouldReturnNullForNonExistentTransaction() {
        Transaction transaction = transactionService.getTransaction("non-existent");
        assertNull(transaction);
    }

    @Test
    @DisplayName("Deve criar transação com tipo correto")
    void shouldCreateTransactionWithCorrectType() {
        Transaction transaction = transactionService.transfer(
            fromAccount.getId(),
            toAccount.getId(),
            new BigDecimal("100.00")
        );
        
        assertEquals(TransactionType.TRANSFER, transaction.getType());
    }

    @Test
    @DisplayName("Deve manter saldos consistentes após transferência falha")
    void shouldKeepBalancesConsistentAfterFailedTransfer() {
        BigDecimal originalFromBalance = accountService.getAccount(fromAccount.getId()).getBalance();
        BigDecimal originalToBalance = accountService.getAccount(toAccount.getId()).getBalance();
        
        transactionService.transfer(
            fromAccount.getId(),
            toAccount.getId(),
            new BigDecimal("5000.00")
        );
        
        assertEquals(originalFromBalance, accountService.getAccount(fromAccount.getId()).getBalance());
        assertEquals(originalToBalance, accountService.getAccount(toAccount.getId()).getBalance());
    }
}
