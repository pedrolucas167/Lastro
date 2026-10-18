package io.github.pedrolucas167.lastro.service;

import io.github.pedrolucas167.lastro.config.PriceBandProperties;
import io.github.pedrolucas167.lastro.domain.Actor;
import io.github.pedrolucas167.lastro.domain.MatchingEngine;
import io.github.pedrolucas167.lastro.domain.Order;
import io.github.pedrolucas167.lastro.domain.OrderSide;
import io.github.pedrolucas167.lastro.domain.OrderType;
import io.github.pedrolucas167.lastro.domain.TimeInForce;
import io.github.pedrolucas167.lastro.exception.OrderIdempotencyKeyReusedException;
import io.github.pedrolucas167.lastro.journal.EventStore;
import io.github.pedrolucas167.lastro.journal.InMemoryEventStore;
import io.github.pedrolucas167.lastro.journal.OrderSubmitted;
import io.github.pedrolucas167.lastro.metrics.MetricsCollector;
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
    private static final Actor BACK_OFFICE = new Actor("operador", Set.of(Actor.OPERATOR));

    @Test
    void retryWithTheSameKeyGetsTheOrderThatWasAlreadyAccepted() {
        EventStore eventStore = new InMemoryEventStore();
        TradingService trading = newTradingService(eventStore);

        Order first = submit(trading, "compra-1").order();
        Order retried = submit(trading, "compra-1").order();

        assertEquals(first.getId(), retried.getId());
        assertEquals(1, journaledOrders(eventStore));
    }

    @Test
    void differentKeysCreateDifferentOrders() {
        EventStore eventStore = new InMemoryEventStore();
        TradingService trading = newTradingService(eventStore);

        Order first = submit(trading, "compra-1").order();
        Order second = submit(trading, "compra-2").order();

        assertNotEquals(first.getId(), second.getId());
        assertEquals(2, journaledOrders(eventStore));
    }

    @Test
    void withoutKeyEachCallCreatesANewOrder() {
        EventStore eventStore = new InMemoryEventStore();
        TradingService trading = newTradingService(eventStore);

        Order first = submit(trading, null).order();
        Order second = submit(trading, null).order();

        assertNotEquals(first.getId(), second.getId());
        assertEquals(2, journaledOrders(eventStore));
    }

    @Test
    void replayBringsTheKeyBackSoARetryAfterRestartDoesNotDuplicate() {
        EventStore eventStore = new InMemoryEventStore();
        Order accepted = submit(newTradingService(eventStore), "compra-1").order();

        // Reinicio: engine novo, mesmas ordens reconstruidas do journal.
        TradingService restarted = newTradingService(eventStore);
        restarted.rebuild(eventStore.readAll());

        Order retried = submit(restarted, "compra-1").order();

        assertEquals(accepted.getId(), retried.getId());
        assertEquals(1, journaledOrders(eventStore));
    }

    @Test
    void sameKeyForAnotherCommandIsAReuseNotARetry() {
        EventStore eventStore = new InMemoryEventStore();
        TradingService trading = newTradingService(eventStore);
        submit(trading, "compra-1");

        assertThrows(OrderIdempotencyKeyReusedException.class,
            () -> trading.submitOrder(BACK_OFFICE, "VALE3", OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("60.00"), 10, "conta-1", TimeInForce.GTC, "compra-1"));
    }

    @Test
    void simultaneousRetriesWithTheSameKeyCreateOneOrder() throws Exception {
        EventStore eventStore = new InMemoryEventStore();
        TradingService trading = newTradingService(eventStore);

        int clients = 8;
        List<String> ids = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(clients);

        List<Future<?>> pending = new ArrayList<>();
        for (int i = 0; i < clients; i++) {
            pending.add(pool.submit(() -> {
                start.await();
                ids.add(submit(trading, "compra-1").order().getId());
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
        assertEquals(1, journaledOrders(eventStore));
    }

    private OrderExecutionResult submit(TradingService trading, String idempotencyKey) {
        return trading.submitOrder(BACK_OFFICE, "PETR4", OrderSide.BUY, OrderType.LIMIT,
            new BigDecimal("38.50"), 10, "conta-1", TimeInForce.GTC, idempotencyKey);
    }

    private long journaledOrders(EventStore eventStore) {
        return eventStore.readAll().stream().filter(OrderSubmitted.class::isInstance).count();
    }

    private TradingService newTradingService(EventStore eventStore) {
        RegulatoryValidationService validation = new RegulatoryValidationService(
            new MarketHaltService(eventStore), new PriceBandProperties(), false, true, "00:00", "23:59");
        return new TradingService(new MatchingEngine(), new OrderService(eventStore),
            new PositionService(), new MetricsCollector(), validation, eventStore);
    }
}
