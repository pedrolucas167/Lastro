package io.github.pedrolucas167.lastro.service;

import io.github.pedrolucas167.lastro.domain.Account;
import io.github.pedrolucas167.lastro.domain.Actor;
import io.github.pedrolucas167.lastro.domain.Transaction;
import io.github.pedrolucas167.lastro.domain.TransactionStatus;
import io.github.pedrolucas167.lastro.exception.TransferIdempotencyKeyReusedException;
import io.github.pedrolucas167.lastro.journal.EventStore;
import io.github.pedrolucas167.lastro.journal.InMemoryEventStore;
import io.github.pedrolucas167.lastro.journal.TransferCompleted;
import io.github.pedrolucas167.lastro.ledger.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** O reenvio da transferencia nao pode movimentar o dinheiro duas vezes. */
class TransferIdempotencyTest {
    private static final Actor BACK_OFFICE = new Actor("operador", Set.of(Actor.OPERATOR));

    private EventStore eventStore;
    private AccountService accountService;
    private TransactionService transactionService;
    private Account from;
    private Account to;

    @BeforeEach
    void setUp() {
        eventStore = new InMemoryEventStore();
        accountService = new AccountService(eventStore, new LedgerService());
        transactionService = new TransactionService(accountService, new ClientService(eventStore));
        from = accountService.createAccount(BACK_OFFICE, new BigDecimal("1000.00"));
        to = accountService.createAccount(BACK_OFFICE, new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("Reenvio com a mesma chave devolve a transferencia feita e move o dinheiro uma vez")
    void resendWithTheSameKeyMovesTheMoneyOnce() {
        Transaction first = transfer("chave-1");
        Transaction resent = transfer("chave-1");

        assertEquals(first.getId(), resent.getId());
        assertEquals(0, new BigDecimal("900.00").compareTo(balanceOf(from)));
        assertEquals(0, new BigDecimal("600.00").compareTo(balanceOf(to)));
        assertEquals(1, journaledTransfers());
    }

    @Test
    @DisplayName("Chaves diferentes movimentam o dinheiro uma vez cada")
    void differentKeysEachMoveTheMoney() {
        Transaction first = transfer("chave-1");
        Transaction second = transfer("chave-2");

        assertNotEquals(first.getId(), second.getId());
        assertEquals(0, new BigDecimal("800.00").compareTo(balanceOf(from)));
        assertEquals(2, journaledTransfers());
    }

    @Test
    @DisplayName("Sem chave cada chamada movimenta o dinheiro")
    void withoutKeyEachCallMovesTheMoney() {
        Transaction first = transfer(null);
        Transaction second = transfer(null);

        assertNotEquals(first.getId(), second.getId());
        assertEquals(0, new BigDecimal("800.00").compareTo(balanceOf(from)));
        assertEquals(2, journaledTransfers());
    }

    @Test
    @DisplayName("O replay traz a chave de volta: o reenvio depois do reinicio nao repete")
    void replayBringsTheKeyBackSoAResendDoesNotRepeat() {
        Transaction accepted = transfer("chave-1");

        // Reinicio: transacoes novas reconstruidas do mesmo journal.
        TransactionService restarted = new TransactionService(accountService, new ClientService(eventStore));
        restarted.rebuild(eventStore.readAll());

        Transaction resent = restarted.transfer(BACK_OFFICE, from.getId(), to.getId(),
            new BigDecimal("100.00"), "chave-1");

        assertEquals(accepted.getId(), resent.getId());
        assertEquals(0, new BigDecimal("900.00").compareTo(balanceOf(from)));
        assertEquals(0, new BigDecimal("600.00").compareTo(balanceOf(to)));
        assertEquals(1, journaledTransfers());
    }

    @Test
    @DisplayName("A mesma chave para outra transferencia e 409, sem movimentar nada")
    void sameKeyForAnotherTransferIsAReuse() {
        transfer("chave-1");

        assertThrows(TransferIdempotencyKeyReusedException.class, () ->
            transactionService.transfer(BACK_OFFICE, to.getId(), from.getId(), new BigDecimal("10.00"), "chave-1"));

        assertEquals(0, new BigDecimal("900.00").compareTo(balanceOf(from)));
        assertEquals(1, journaledTransfers());
    }

    @Test
    @DisplayName("Transferencia que falhou nao prende a chave: o reenvio tenta de novo")
    void failedTransferDoesNotHoldTheKey() {
        Account poor = accountService.createAccount(BACK_OFFICE, new BigDecimal("10.00"));

        Transaction failed = transactionService.transfer(BACK_OFFICE, poor.getId(), to.getId(),
            new BigDecimal("100.00"), "chave-1");
        assertEquals(TransactionStatus.FAILED, failed.getStatus());

        // O dinheiro entrou depois: o reenvio com a mesma chave tenta de novo e agora passa.
        accountService.getAccount(poor.getId()).credit(new BigDecimal("200.00"));
        Transaction resent = transactionService.transfer(BACK_OFFICE, poor.getId(), to.getId(),
            new BigDecimal("100.00"), "chave-1");

        assertEquals(TransactionStatus.COMPLETED, resent.getStatus());
        assertEquals(0, new BigDecimal("110.00").compareTo(balanceOf(poor)));
        assertEquals(1, journaledTransfers());
    }

    @Test
    @DisplayName("Reenvios simultaneos com a mesma chave movimentam o dinheiro uma vez")
    void simultaneousResendsMoveTheMoneyOnce() throws Exception {
        int clients = 8;
        List<String> ids = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(clients);

        List<Future<?>> pending = new ArrayList<>();
        for (int i = 0; i < clients; i++) {
            pending.add(pool.submit(() -> {
                start.await();
                ids.add(transfer("chave-1").getId());
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : pending) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertEquals(clients, ids.size());
        assertEquals(1, Set.copyOf(ids).size());
        assertEquals(0, new BigDecimal("900.00").compareTo(balanceOf(from)));
        assertEquals(0, new BigDecimal("600.00").compareTo(balanceOf(to)));
        assertEquals(1, journaledTransfers());
    }

    private Transaction transfer(String idempotencyKey) {
        return transactionService.transfer(BACK_OFFICE, from.getId(), to.getId(),
            new BigDecimal("100.00"), idempotencyKey);
    }

    private BigDecimal balanceOf(Account account) {
        return accountService.getAccount(account.getId()).getBalance();
    }

    private long journaledTransfers() {
        return eventStore.readAll().stream().filter(TransferCompleted.class::isInstance).count();
    }
}
