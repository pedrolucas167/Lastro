package org.example.lastro.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MatchingEngineReplaceTest {
    private static final String SYMBOL = "PETR4";
    private static final String ACCOUNT = "acc-1";

    private final MatchingEngine engine = new MatchingEngine();

    private Order order(OrderSide side, String price, int quantity, TimeInForce timeInForce) {
        return new Order(UUID.randomUUID().toString(), SYMBOL, side, OrderType.LIMIT,
            new BigDecimal(price), new BigDecimal(quantity), ACCOUNT, timeInForce);
    }

    private OrderBook book() {
        return engine.getOrderBook(SYMBOL);
    }

    @Test
    void replaceChangesPriceAndQuantity() {
        Order buy = order(OrderSide.BUY, "10.00", 100, TimeInForce.GTC);
        engine.submitOrder(buy);

        Optional<List<Trade>> trades = engine.replaceOrder(
            buy.getId(), SYMBOL, new BigDecimal("10.50"), new BigDecimal(200));

        assertTrue(trades.isPresent());
        assertTrue(trades.get().isEmpty());
        assertEquals(new BigDecimal("10.50"), buy.getPrice());
        assertEquals(200, buy.getRemainingQuantity());
        assertEquals(OrderStatus.PENDING, buy.getStatus());
        assertTrue(book().getBidsAtPrice(new BigDecimal("10.00")).isEmpty());
        assertEquals(0, new BigDecimal("10.50").compareTo(book().getBestBid().orElseThrow().getPrice()));
    }

    @Test
    void replaceCanCrossTheBookAndExecute() {
        engine.submitOrder(order(OrderSide.SELL, "10.00", 100, TimeInForce.GTC));

        Order buy = order(OrderSide.BUY, "9.00", 100, TimeInForce.GTC);
        engine.submitOrder(buy);

        Optional<List<Trade>> trades = engine.replaceOrder(
            buy.getId(), SYMBOL, new BigDecimal("10.00"), new BigDecimal(100));

        assertTrue(trades.isPresent());
        assertEquals(1, trades.get().size());
        assertEquals(100, trades.get().getFirst().getQuantity());
        assertEquals(OrderStatus.FILLED, buy.getStatus());
        assertTrue(book().getBestBid().isEmpty());
        assertTrue(book().getBestAsk().isEmpty());
    }

    @Test
    void replacePreservesFilledQuantityWhenIncreasingQuantity() {
        engine.submitOrder(order(OrderSide.SELL, "10.00", 100, TimeInForce.GTC));

        Order buy = order(OrderSide.BUY, "10.00", 150, TimeInForce.GTC);
        engine.submitOrder(buy);

        Optional<List<Trade>> trades = engine.replaceOrder(
            buy.getId(), SYMBOL, new BigDecimal("9.50"), new BigDecimal(200));

        assertTrue(trades.isPresent());
        assertEquals(100, buy.getRemainingQuantity());
        assertEquals(OrderStatus.PARTIALLY_FILLED, buy.getStatus());
        assertEquals(new BigDecimal("9.50"), buy.getPrice());
    }

    @Test
    void replaceBelowFilledQuantityIsRejected() {
        engine.submitOrder(order(OrderSide.SELL, "10.00", 100, TimeInForce.GTC));

        Order buy = order(OrderSide.BUY, "10.00", 150, TimeInForce.GTC);
        engine.submitOrder(buy);

        Optional<List<Trade>> trades = engine.replaceOrder(
            buy.getId(), SYMBOL, new BigDecimal("10.00"), new BigDecimal(30));

        assertTrue(trades.isEmpty());
        assertEquals(50, buy.getRemainingQuantity());
        assertEquals(OrderStatus.PARTIALLY_FILLED, buy.getStatus());
        assertEquals(50, book().getBestBid().orElseThrow().getRemainingQuantity());
    }

    @Test
    void replaceLosesTimePriority() {
        Order first = order(OrderSide.BUY, "10.00", 100, TimeInForce.GTC);
        engine.submitOrder(first);
        Order second = order(OrderSide.BUY, "10.00", 100, TimeInForce.GTC);
        engine.submitOrder(second);

        engine.replaceOrder(first.getId(), SYMBOL, new BigDecimal("10.00"), new BigDecimal(100));

        List<Trade> trades = engine.submitOrder(order(OrderSide.SELL, "10.00", 100, TimeInForce.GTC));

        assertEquals(second.getId(), trades.getFirst().getBuyOrderId());
    }

    @Test
    void replaceUnknownOrderReturnsEmpty() {
        assertTrue(engine.replaceOrder("missing", SYMBOL, new BigDecimal("10.00"),
            new BigDecimal(100)).isEmpty());
    }
}
