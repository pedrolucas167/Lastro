package io.github.pedrolucas167.lastro.concurrency;

import io.github.pedrolucas167.lastro.domain.Account;
import io.github.pedrolucas167.lastro.domain.Actor;
import io.github.pedrolucas167.lastro.journal.EventStore;
import io.github.pedrolucas167.lastro.journal.InMemoryEventStore;
import io.github.pedrolucas167.lastro.service.AccountService;
import io.github.pedrolucas167.lastro.service.IdempotencyService;
import io.github.pedrolucas167.lastro.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrencyTest {
    private static final Actor BACK_OFFICE = new Actor("operador", Set.of(Actor.OPERATOR));

    private AccountService accountService;
    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        EventStore eventStore = new InMemoryEventStore();
        accountService = new AccountService(eventStore);
        IdempotencyService idempotencyService = new IdempotencyService();
        transactionService = new TransactionService(accountService, idempotencyService, eventStore);
    }

    @Test
    @DisplayName("Deve manter consistência em créditos concorrentes")
    void shouldMaintainConsistencyWithConcurrentCredits() throws Exception {
        Account account = accountService.createAccount(BACK_OFFICE, BigDecimal.ZERO);
        int numThreads = 100;
        BigDecimal creditAmount = new BigDecimal("10.00");
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    account.credit(creditAmount);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        BigDecimal expectedBalance = creditAmount.multiply(new BigDecimal(numThreads));
        assertEquals(expectedBalance, account.getBalance());
    }

    @Test
    @DisplayName("Deve manter consistência em débitos concorrentes com saldo suficiente")
    void shouldMaintainConsistencyWithConcurrentDebits() throws Exception {
        BigDecimal initialBalance = new BigDecimal("10000.00");
        Account account = accountService.createAccount(BACK_OFFICE, initialBalance);
        int numThreads = 100;
        BigDecimal debitAmount = new BigDecimal("10.00");
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    if (account.tryDebit(debitAmount)) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        BigDecimal expectedBalance = initialBalance.subtract(
            debitAmount.multiply(new BigDecimal(successCount.get()))
        );
        assertEquals(expectedBalance, account.getBalance());
        assertEquals(numThreads, successCount.get());
    }

    @Test
    @DisplayName("Deve rejeitar débitos concorrentes quando saldo insuficiente")
    void shouldRejectConcurrentDebitsWhenInsufficientBalance() throws Exception {
        BigDecimal initialBalance = new BigDecimal("100.00");
        Account account = accountService.createAccount(BACK_OFFICE, initialBalance);
        int numThreads = 100;
        BigDecimal debitAmount = new BigDecimal("10.00");
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    if (account.tryDebit(debitAmount)) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(10, successCount.get());
        assertEquals(90, failureCount.get());
        assertEquals(0, account.getBalance().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Deve manter consistência em transferências concorrentes")
    void shouldMaintainConsistencyWithConcurrentTransfers() throws Exception {
        Account account1 = accountService.createAccount(BACK_OFFICE, new BigDecimal("10000.00"));
        Account account2 = accountService.createAccount(BACK_OFFICE, new BigDecimal("10000.00"));
        
        int numThreads = 100;
        BigDecimal transferAmount = new BigDecimal("10.00");
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            final boolean direction = i % 2 == 0;
            executor.submit(() -> {
                try {
                    String from = direction ? account1.getId() : account2.getId();
                    String to = direction ? account2.getId() : account1.getId();
                    
                    var transaction = transactionService.transfer(BACK_OFFICE, from, to, transferAmount);
                    if (transaction.getStatus() == io.github.pedrolucas167.lastro.domain.TransactionStatus.COMPLETED) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        BigDecimal totalBalance = account1.getBalance().add(account2.getBalance());
        assertEquals(new BigDecimal("20000.00"), totalBalance);
        assertEquals(numThreads, successCount.get());
    }

    @Test
    @DisplayName("Não deve ter deadlock em transferências cíclicas")
    void shouldNotDeadlockInCyclicTransfers() throws Exception {
        Account account1 = accountService.createAccount(BACK_OFFICE, new BigDecimal("1000.00"));
        Account account2 = accountService.createAccount(BACK_OFFICE, new BigDecimal("1000.00"));
        Account account3 = accountService.createAccount(BACK_OFFICE, new BigDecimal("1000.00"));
        
        int numThreads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger completedCount = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            final int threadNum = i;
            executor.submit(() -> {
                try {
                    String from, to;
                    if (threadNum % 3 == 0) {
                        from = account1.getId();
                        to = account2.getId();
                    } else if (threadNum % 3 == 1) {
                        from = account2.getId();
                        to = account3.getId();
                    } else {
                        from = account3.getId();
                        to = account1.getId();
                    }
                    
                    var transaction = transactionService.transfer(BACK_OFFICE, from, to, new BigDecimal("10.00"));
                    if (transaction.getStatus() == io.github.pedrolucas167.lastro.domain.TransactionStatus.COMPLETED) {
                        completedCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Test should complete without deadlock");
        
        BigDecimal totalBalance = account1.getBalance()
            .add(account2.getBalance())
            .add(account3.getBalance());
        assertEquals(new BigDecimal("3000.00"), totalBalance);
    }

    @Test
    @DisplayName("Deve preservar invariantes de saldo sob alta concorrência")
    void shouldPreserveBalanceInvariantsUnderHighConcurrency() throws Exception {
        Account account = accountService.createAccount(BACK_OFFICE, new BigDecimal("100000.00"));
        int numOperations = 1000;
        BigDecimal operationAmount = new BigDecimal("1.00");
        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(numOperations);

        for (int i = 0; i < numOperations; i++) {
            final boolean isCredit = i % 2 == 0;
            executor.submit(() -> {
                try {
                    if (isCredit) {
                        account.credit(operationAmount);
                    } else {
                        account.tryDebit(operationAmount);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        BigDecimal finalBalance = account.getBalance();
        assertTrue(finalBalance.compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(finalBalance.compareTo(new BigDecimal("100000.00")) <= 0);
    }
}
