package org.example.lastro.service;

import org.example.lastro.config.PriceBandProperties;
import org.example.lastro.domain.MatchingEngine;
import org.example.lastro.domain.Order;
import org.example.lastro.domain.OrderBook;
import org.example.lastro.domain.OrderSide;
import org.example.lastro.domain.OrderType;
import org.example.lastro.domain.TimeInForce;
import org.example.lastro.domain.Trade;
import org.example.lastro.journal.EventStore;
import org.example.lastro.journal.InMemoryEventStore;
import org.example.lastro.metrics.MetricsCollector;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TradingJournalReplayTest {

    @Test
    void rebuildsBookPositionsAndTradesFromJournal() {
        EventStore eventStore = new InMemoryEventStore();
        TradingService writer = newTradingService(eventStore);

        writer.submitOrder("PETR4", OrderSide.SELL, OrderType.LIMIT,
            new BigDecimal("38.50"), 100, "seller", TimeInForce.GTC);
        writer.submitOrder("PETR4", OrderSide.BUY, OrderType.LIMIT,
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

        Order kept = writer.submitOrder("VALE3", OrderSide.BUY, OrderType.LIMIT,
            new BigDecimal("60.00"), 100, "a1", TimeInForce.GTC).order();
        Order cancelled = writer.submitOrder("VALE3", OrderSide.BUY, OrderType.LIMIT,
            new BigDecimal("60.00"), 50, "a2", TimeInForce.GTC).order();

        assertTrue(writer.cancelOrder(cancelled.getId(), "VALE3"));
        assertTrue(writer.replaceOrder(kept.getId(), "VALE3", new BigDecimal("61.00"), null).isPresent());

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

        Order first = writer.submitOrder("ITUB4", OrderSide.BUY, OrderType.LIMIT,
            new BigDecimal("30.00"), 100, "a1", TimeInForce.GTC).order();
        Order second = writer.submitOrder("ITUB4", OrderSide.BUY, OrderType.LIMIT,
            new BigDecimal("30.00"), 100, "a2", TimeInForce.GTC).order();

        // Amend no mesmo preco: a ordem volta ao fim da fila (perde prioridade).
        assertTrue(writer.replaceOrder(first.getId(), "ITUB4", new BigDecimal("30.00"), null).isPresent());

        TradingService replayed = newTradingService(eventStore);
        replayed.rebuild(eventStore.readAll());

        List<String> priority = replayed.getOrderBook("ITUB4")
            .getBidsAtPrice(new BigDecimal("30.00")).stream().map(Order::getId).toList();
        assertEquals(List.of(second.getId(), first.getId()), priority);
    }

    private TradingService newTradingService(EventStore eventStore) {
        RegulatoryValidationService validation = new RegulatoryValidationService(
            new PriceBandProperties(), false, true, "00:00", "23:59");
        return new TradingService(new MatchingEngine(), new OrderService(eventStore),
            new PositionService(), new MetricsCollector(), validation, eventStore);
    }
}
