package io.github.pedrolucas167.lastro.service;

import io.github.pedrolucas167.lastro.domain.Order;
import io.github.pedrolucas167.lastro.domain.OrderSide;
import io.github.pedrolucas167.lastro.domain.OrderType;
import io.github.pedrolucas167.lastro.domain.TimeInForce;
import io.github.pedrolucas167.lastro.exception.OrderIdempotencyKeyReusedException;
import io.github.pedrolucas167.lastro.journal.InMemoryEventStore;
import io.github.pedrolucas167.lastro.journal.OrderSubmitted;
import io.github.pedrolucas167.lastro.support.TradingFixture;
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

/** O retry do cliente nao pode virar ordem nova: quem segura isso e a chave de idempotencia. */
class OrderIdempotencyTest {

    @Test
    void retryWithTheSameKeyGetsTheOrderThatWasAlreadyAccepted() {
        TradingFixture fixture = newFixture();
        String account = fixture.fundedAccount("100000.00");

        Order first = submit(fixture, account, "compra-1").order();
        Order retried = submit(fixture, account, "compra-1").order();

        assertEquals(first.getId(), retried.getId());
        assertEquals(1, journaledOrders(fixture));
    }

    @Test
    void differentKeysCreateDifferentOrders() {
        TradingFixture fixture = newFixture();
        String account = fixture.fundedAccount("100000.00");

        Order first = submit(fixture, account, "compra-1").order();
        Order second = submit(fixture, account, "compra-2").order();

        assertNotEquals(first.getId(), second.getId());
        assertEquals(2, journaledOrders(fixture));
    }

    @Test
    void withoutKeyEachCallCreatesANewOrder() {
        TradingFixture fixture = newFixture();
        String account = fixture.fundedAccount("100000.00");

        Order first = submit(fixture, account, null).order();
        Order second = submit(fixture, account, null).order();

        assertNotEquals(first.getId(), second.getId());
        assertEquals(2, journaledOrders(fixture));
    }

    @Test
    void replayBringsTheKeyBackSoARetryAfterRestartDoesNotDuplicate() {
        TradingFixture fixture = newFixture();
        String account = fixture.fundedAccount("100000.00");
        Order accepted = submit(fixture, account, "compra-1").order();

        // Reinicio: engine novo, mesmas ordens reconstruidas do journal.
        TradingFixture restarted = fixture.restart();

        Order retried = submit(restarted, account, "compra-1").order();

        assertEquals(accepted.getId(), retried.getId());
        assertEquals(1, journaledOrders(fixture));
    }

    @Test
    void sameKeyForAnotherCommandIsAReuseNotARetry() {
        TradingFixture fixture = newFixture();
        String account = fixture.fundedAccount("100000.00");
        submit(fixture, account, "compra-1");

        assertThrows(OrderIdempotencyKeyReusedException.class,
            () -> fixture.trading.submitOrder(TradingFixture.BACK_OFFICE, "VALE3", OrderSide.BUY,
                OrderType.LIMIT, new BigDecimal("60.00"), 10, account, TimeInForce.GTC,
                "compra-1"));
    }

    @Test
    void simultaneousRetriesWithTheSameKeyCreateOneOrder() throws Exception {
        TradingFixture fixture = newFixture();
        String account = fixture.fundedAccount("100000.00");

        int clients = 8;
        List<String> ids = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(clients);

        List<Future<?>> pending = new ArrayList<>();
        for (int i = 0; i < clients; i++) {
            pending.add(pool.submit(() -> {
                start.await();
                ids.add(submit(fixture, account, "compra-1").order().getId());
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
        assertEquals(1, journaledOrders(fixture));
    }

    private TradingFixture newFixture() {
        return new TradingFixture(new InMemoryEventStore());
    }

    private OrderExecutionResult submit(TradingFixture fixture, String accountId, String idempotencyKey) {
        return fixture.trading.submitOrder(TradingFixture.BACK_OFFICE, "PETR4", OrderSide.BUY,
            OrderType.LIMIT, new BigDecimal("38.50"), 10, accountId, TimeInForce.GTC, idempotencyKey);
    }

    private long journaledOrders(TradingFixture fixture) {
        return fixture.eventStore.readAll().stream().filter(OrderSubmitted.class::isInstance).count();
    }
}
