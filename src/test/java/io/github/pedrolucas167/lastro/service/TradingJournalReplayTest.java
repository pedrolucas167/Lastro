package io.github.pedrolucas167.lastro.service;

import io.github.pedrolucas167.lastro.config.PriceBandProperties;
import io.github.pedrolucas167.lastro.domain.Actor;
import io.github.pedrolucas167.lastro.domain.MatchingEngine;
import io.github.pedrolucas167.lastro.domain.Order;
import io.github.pedrolucas167.lastro.domain.OrderBook;
import io.github.pedrolucas167.lastro.domain.OrderSide;
import io.github.pedrolucas167.lastro.domain.OrderType;
import io.github.pedrolucas167.lastro.domain.TimeInForce;
import io.github.pedrolucas167.lastro.domain.Trade;
import io.github.pedrolucas167.lastro.journal.EventStore;
import io.github.pedrolucas167.lastro.journal.InMemoryEventStore;
import io.github.pedrolucas167.lastro.metrics.MetricsCollector;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TradingJournalReplayTest {
    private static final Actor BACK_OFFICE = new Actor("operador", Set.of(Actor.OPERATOR));

    @Test
    void rebuildsBookPositionsAndTradesFromJournal() {
        EventStore eventStore = new InMemoryEventStore();
        TradingService writer = newTradingService(eventStore);

        writer.submitOrder(BACK_OFFICE, "PETR4", OrderSide.SELL, OrderType.LIMIT,
            new BigDecimal("38.50"), 100, "seller", TimeInForce.GTC);
        writer.submitOrder(BACK_OFFICE, "PETR4", OrderSide.BUY, OrderType.LIMIT,
            new BigDecimal("38.50"), 40, "buyer", TimeInForce.GTC);

        List<Trade> before = writer.getTrades();
        assertEquals(1, before.size());

        // Simula um reinicio: engine novo, mesmo journal.
        TradingService replayed = newTradingService(eventStore);
        replayed.rebuild(eventStore.readAll());

        List<Trade> after = replayed.getTrades();
        assertEquals(1, after.size());
        assertEquals(before.getFirst().getId(), after.getFirst().getId());
        assertEquals(before.getFirst().getTimestamp(), after.getFirst().getTimestamp());
        assertEquals(0, new BigDecimal("38.50").compareTo(after.getFirst().getPrice()));
        assertEquals(40, after.getFirst().getQuantity());

        assertEquals(40, replayed.getPositionQuantity("buyer", "PETR4"));
        assertEquals(-40, replayed.getPositionQuantity("seller", "PETR4"));

        OrderBook book = replayed.getOrderBook("PETR4");
        assertEquals(0, new BigDecimal("38.50").compareTo(book.getBestAsk().orElseThrow().getPrice()));
        assertEquals(60, book.getTotalAskQuantity());
    }

    @Test
    void rebuildsCancelledAndReplacedOrders() {
        EventStore eventStore = new InMemoryEventStore();
        TradingService writer = newTradingService(eventStore);

        Order kept = writer.submitOrder(BACK_OFFICE, "VALE3", OrderSide.BUY, OrderType.LIMIT,
            new BigDecimal("60.00"), 100, "a1", TimeInForce.GTC).order();
        Order cancelled = writer.submitOrder(BACK_OFFICE, "VALE3", OrderSide.BUY, OrderType.LIMIT,
            new BigDecimal("60.00"), 50, "a2", TimeInForce.GTC).order();

        assertEquals(TradingService.CancelOutcome.CANCELLED,
            writer.cancelOrder(BACK_OFFICE, cancelled.getId(), "VALE3"));
        assertTrue(writer.replaceOrder(BACK_OFFICE, kept.getId(), "VALE3", new BigDecimal("61.00"), null).isPresent());

        TradingService replayed = newTradingService(eventStore);
        replayed.rebuild(eventStore.readAll());

        OrderBook book = replayed.getOrderBook("VALE3");
        assertEquals(0, new BigDecimal("61.00").compareTo(book.getBestBid().orElseThrow().getPrice()));
        assertEquals(100, book.getTotalBidQuantity());
        assertTrue(book.getBidsAtPrice(new BigDecimal("60.00")).isEmpty());
    }

    @Test
    void preservesPriceTimePriorityAfterReplace() {
        EventStore eventStore = new InMemoryEventStore();
        TradingService writer = newTradingService(eventStore);

        Order first = writer.submitOrder(BACK_OFFICE, "ITUB4", OrderSide.BUY, OrderType.LIMIT,
            new BigDecimal("30.00"), 100, "a1", TimeInForce.GTC).order();
        Order second = writer.submitOrder(BACK_OFFICE, "ITUB4", OrderSide.BUY, OrderType.LIMIT,
            new BigDecimal("30.00"), 100, "a2", TimeInForce.GTC).order();

        // Amend no mesmo preco: a ordem volta ao fim da fila (perde prioridade).
        assertTrue(writer.replaceOrder(BACK_OFFICE, first.getId(), "ITUB4", new BigDecimal("30.00"), null).isPresent());

        TradingService replayed = newTradingService(eventStore);
        replayed.rebuild(eventStore.readAll());

        List<String> priority = replayed.getOrderBook("ITUB4")
            .getBidsAtPrice(new BigDecimal("30.00")).stream().map(Order::getId).toList();
        assertEquals(List.of(second.getId(), first.getId()), priority);
    }

    private TradingService newTradingService(EventStore eventStore) {
        RegulatoryValidationService validation = new RegulatoryValidationService(
            new MarketHaltService(eventStore), new PriceBandProperties(), false, true, "00:00", "23:59");
        return new TradingService(new MatchingEngine(), new OrderService(eventStore),
            new PositionService(), new MetricsCollector(), validation, eventStore);
    }
}
