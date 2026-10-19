package io.github.pedrolucas167.lastro.service;

import io.github.pedrolucas167.lastro.domain.Order;
import io.github.pedrolucas167.lastro.domain.OrderBook;
import io.github.pedrolucas167.lastro.domain.OrderSide;
import io.github.pedrolucas167.lastro.domain.OrderType;
import io.github.pedrolucas167.lastro.domain.TimeInForce;
import io.github.pedrolucas167.lastro.domain.Trade;
import io.github.pedrolucas167.lastro.journal.InMemoryEventStore;
import io.github.pedrolucas167.lastro.support.TradingFixture;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TradingJournalReplayTest {

    @Test
    void rebuildsBookPositionsAndTradesFromJournal() {
        TradingFixture fixture = new TradingFixture(new InMemoryEventStore());
        String seller = fixture.accountWithShares("0.00", "PETR4", 100, "38.50");
        String buyer = fixture.fundedAccount("10000.00");

        fixture.trading.submitOrder(TradingFixture.BACK_OFFICE, "PETR4", OrderSide.SELL,
            OrderType.LIMIT, new BigDecimal("38.50"), 100, seller, TimeInForce.GTC);
        fixture.trading.submitOrder(TradingFixture.BACK_OFFICE, "PETR4", OrderSide.BUY,
            OrderType.LIMIT, new BigDecimal("38.50"), 40, buyer, TimeInForce.GTC);

        List<Trade> before = fixture.trading.getTrades();
        assertEquals(1, before.size());

        // Simula um reinicio: engine novo, mesmo journal.
        TradingService replayed = fixture.restart().trading;

        List<Trade> after = replayed.getTrades();
        assertEquals(1, after.size());
        assertEquals(before.getFirst().getId(), after.getFirst().getId());
        assertEquals(before.getFirst().getTimestamp(), after.getFirst().getTimestamp());
        assertEquals(0, new BigDecimal("38.50").compareTo(after.getFirst().getPrice()));
        assertEquals(40, after.getFirst().getQuantity());

        assertEquals(40, replayed.getPositionQuantity(buyer, "PETR4"));
        assertEquals(60, replayed.getPositionQuantity(seller, "PETR4"));

        OrderBook book = replayed.getOrderBook("PETR4");
        assertEquals(0, new BigDecimal("38.50").compareTo(book.getBestAsk().orElseThrow().getPrice()));
        assertEquals(60, book.getTotalAskQuantity());
    }

    @Test
    void rebuildsCancelledAndReplacedOrders() {
        TradingFixture fixture = new TradingFixture(new InMemoryEventStore());
        String first = fixture.fundedAccount("100000.00");
        String second = fixture.fundedAccount("100000.00");

        Order kept = fixture.trading.submitOrder(TradingFixture.BACK_OFFICE, "VALE3",
            OrderSide.BUY, OrderType.LIMIT, new BigDecimal("60.00"), 100, first,
            TimeInForce.GTC).order();
        Order cancelled = fixture.trading.submitOrder(TradingFixture.BACK_OFFICE, "VALE3",
            OrderSide.BUY, OrderType.LIMIT, new BigDecimal("60.00"), 50, second,
            TimeInForce.GTC).order();

        assertEquals(TradingService.CancelOutcome.CANCELLED,
            fixture.trading.cancelOrder(TradingFixture.BACK_OFFICE, cancelled.getId(), "VALE3"));
        assertTrue(fixture.trading.replaceOrder(TradingFixture.BACK_OFFICE, kept.getId(), "VALE3",
            new BigDecimal("61.00"), null).isPresent());

        OrderBook book = fixture.restart().trading.getOrderBook("VALE3");
        assertEquals(0, new BigDecimal("61.00").compareTo(book.getBestBid().orElseThrow().getPrice()));
        assertEquals(100, book.getTotalBidQuantity());
        assertTrue(book.getBidsAtPrice(new BigDecimal("60.00")).isEmpty());
    }

    @Test
    void preservesPriceTimePriorityAfterReplace() {
        TradingFixture fixture = new TradingFixture(new InMemoryEventStore());
        String firstAccount = fixture.fundedAccount("100000.00");
        String secondAccount = fixture.fundedAccount("100000.00");

        Order first = fixture.trading.submitOrder(TradingFixture.BACK_OFFICE, "ITUB4",
            OrderSide.BUY, OrderType.LIMIT, new BigDecimal("30.00"), 100, firstAccount,
            TimeInForce.GTC).order();
        Order second = fixture.trading.submitOrder(TradingFixture.BACK_OFFICE, "ITUB4",
            OrderSide.BUY, OrderType.LIMIT, new BigDecimal("30.00"), 100, secondAccount,
            TimeInForce.GTC).order();

        // Amend no mesmo preco: a ordem volta ao fim da fila (perde prioridade).
        assertTrue(fixture.trading.replaceOrder(TradingFixture.BACK_OFFICE, first.getId(), "ITUB4",
            new BigDecimal("30.00"), null).isPresent());

        List<String> priority = fixture.restart().trading.getOrderBook("ITUB4")
            .getBidsAtPrice(new BigDecimal("30.00")).stream().map(Order::getId).toList();
        assertEquals(List.of(second.getId(), first.getId()), priority);
    }
}
