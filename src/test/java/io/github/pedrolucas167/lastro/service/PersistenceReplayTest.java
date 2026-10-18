package io.github.pedrolucas167.lastro.service;

import io.github.pedrolucas167.lastro.domain.Account;
import io.github.pedrolucas167.lastro.domain.Actor;
import io.github.pedrolucas167.lastro.domain.Transaction;
import io.github.pedrolucas167.lastro.domain.TransactionStatus;
import io.github.pedrolucas167.lastro.journal.EventStore;
import io.github.pedrolucas167.lastro.journal.InMemoryEventStore;
import io.github.pedrolucas167.lastro.journal.JournalReplay;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PersistenceReplayTest {
    private static final Actor BACK_OFFICE = new Actor("operador", true);

    @Test
    void rebuildsAccountsAndTransactionsFromJournal() {
        EventStore eventStore = new InMemoryEventStore();
        AccountService accountService = new AccountService(eventStore);
        TransactionService transactionService =
            new TransactionService(accountService, new IdempotencyService(), eventStore);

        Account from = accountService.createAccount(new BigDecimal("1000.00"));
        Account to = accountService.createAccount(new BigDecimal("500.00"));
        accountService.credit(from.getId(), new BigDecimal("100.00"));
        accountService.debit(from.getId(), new BigDecimal("50.00"));
        Transaction transfer = transactionService.transfer(BACK_OFFICE, from.getId(), to.getId(), new BigDecimal("200.00"));

        // Simula um reinicio: servicos novos, mesmo journal.
        AccountService replayedAccounts = new AccountService(eventStore);
        TransactionService replayedTransactions =
            new TransactionService(replayedAccounts, new IdempotencyService(), eventStore);
        replayedAccounts.rebuild(eventStore.readAll());
        replayedTransactions.rebuild(eventStore.readAll());

        assertEquals(0, new BigDecimal("850.00").compareTo(replayedAccounts.getAccount(from.getId()).getBalance()));
        assertEquals(0, new BigDecimal("700.00").compareTo(replayedAccounts.getAccount(to.getId()).getBalance()));

        Transaction replayed = replayedTransactions.getTransaction(BACK_OFFICE, transfer.getId());
        assertNotNull(replayed);
        assertEquals(TransactionStatus.COMPLETED, replayed.getStatus());
        assertEquals(0, new BigDecimal("200.00").compareTo(replayed.getAmount()));
    }

    @Test
    void failedTransferIsNotJournaled() {
        EventStore eventStore = new InMemoryEventStore();
        AccountService accountService = new AccountService(eventStore);
        TransactionService transactionService =
            new TransactionService(accountService, new IdempotencyService(), eventStore);

        Account poor = accountService.createAccount(new BigDecimal("10.00"));
        Account rich = accountService.createAccount(new BigDecimal("0.00"));

        Transaction transfer = transactionService.transfer(BACK_OFFICE, poor.getId(), rich.getId(), new BigDecimal("999.00"));

        assertEquals(TransactionStatus.FAILED, transfer.getStatus());

        AccountService replayed = new AccountService(eventStore);
        replayed.rebuild(eventStore.readAll());
        assertEquals(0, new BigDecimal("10.00").compareTo(replayed.getAccount(poor.getId()).getBalance()));
    }

    @Test
    void replayRunnerRebuildsStateFromJournal() {
        EventStore eventStore = new InMemoryEventStore();
        AccountService writerAccounts = new AccountService(eventStore);
        TransactionService writerTransactions =
            new TransactionService(writerAccounts, new IdempotencyService(), eventStore);

        Account a = writerAccounts.createAccount(new BigDecimal("100.00"));
        Account b = writerAccounts.createAccount(new BigDecimal("0.00"));
        writerAccounts.credit(a.getId(), new BigDecimal("50.00"));
        Transaction transfer = writerTransactions.transfer(BACK_OFFICE, a.getId(), b.getId(), new BigDecimal("30.00"));

        AccountService readerAccounts = new AccountService(eventStore);
        TransactionService readerTransactions =
            new TransactionService(readerAccounts, new IdempotencyService(), eventStore);
        new JournalReplay(eventStore, List.of(readerAccounts, readerTransactions)).run(null);

        assertEquals(0, new BigDecimal("120.00").compareTo(readerAccounts.getAccount(a.getId()).getBalance()));
        assertEquals(0, new BigDecimal("30.00").compareTo(readerAccounts.getAccount(b.getId()).getBalance()));
        assertNotNull(readerTransactions.getTransaction(BACK_OFFICE, transfer.getId()));
    }

    @Test
    void replaysAccountHolder() {
        EventStore eventStore = new InMemoryEventStore();
        AccountService writer = new AccountService(eventStore);
        Account held = writer.createAccount(new BigDecimal("100.00"), "cliente-1");
        Account unassigned = writer.createAccount(new BigDecimal("100.00"));

        AccountService replayed = new AccountService(eventStore);
        replayed.rebuild(eventStore.readAll());

        assertEquals("cliente-1", replayed.getAccount(held.getId()).getHolder());
        assertTrue(replayed.getAccount(held.getId()).heldBy("cliente-1"));
        assertNull(replayed.getAccount(unassigned.getId()).getHolder());
        assertEquals(List.of(held.getId()), replayed.accountsOf("cliente-1").stream().map(Account::getId).toList());
    }
}
