package io.github.pedrolucas167.lastro.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MatchingEngineTimeInForceTest {
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
    void iocOrderWithoutLiquidityIsCancelledAndDoesNotRest() {
        Order ioc = order(OrderSide.BUY, "10.00", 100, TimeInForce.IOC);

        List<Trade> trades = engine.submitOrder(ioc);

        assertTrue(trades.isEmpty());
        assertEquals(OrderStatus.CANCELLED, ioc.getStatus());
        assertEquals(100, ioc.getRemainingQuantity());
        assertTrue(book().getBestBid().isEmpty());
        assertTrue(book().getBestAsk().isEmpty());
    }

    @Test
    void iocOrderFillsWhatItCanAndCancelsTheRest() {
        engine.submitOrder(order(OrderSide.SELL, "10.00", 100, TimeInForce.GTC));

        Order ioc = order(OrderSide.BUY, "10.00", 150, TimeInForce.IOC);
        List<Trade> trades = engine.submitOrder(ioc);

        assertEquals(1, trades.size());
        assertEquals(100, trades.getFirst().getQuantity());
        assertEquals(OrderStatus.CANCELLED, ioc.getStatus());
        assertEquals(50, ioc.getRemainingQuantity());
        assertTrue(book().getBestBid().isEmpty());
        assertTrue(book().getBestAsk().isEmpty());
    }

    @Test
    void fokOrderWithoutFullLiquidityIsCancelledWithoutTrading() {
        engine.submitOrder(order(OrderSide.SELL, "10.00", 100, TimeInForce.GTC));

        Order fok = order(OrderSide.BUY, "10.00", 150, TimeInForce.FOK);
        List<Trade> trades = engine.submitOrder(fok);

        assertTrue(trades.isEmpty());
        assertEquals(OrderStatus.CANCELLED, fok.getStatus());
        assertEquals(150, fok.getRemainingQuantity());
        assertEquals(100, book().getBestAsk().orElseThrow().getRemainingQuantity());
    }

    @Test
    void fokOrderWithFullLiquidityFillsEntirely() {
        engine.submitOrder(order(OrderSide.SELL, "10.00", 100, TimeInForce.GTC));
        engine.submitOrder(order(OrderSide.SELL, "10.00", 50, TimeInForce.GTC));

        Order fok = order(OrderSide.BUY, "10.00", 150, TimeInForce.FOK);
        List<Trade> trades = engine.submitOrder(fok);

        assertEquals(2, trades.size());
        assertEquals(150, trades.stream().mapToInt(Trade::getQuantity).sum());
        assertEquals(OrderStatus.FILLED, fok.getStatus());
        assertEquals(0, fok.getRemainingQuantity());
        assertTrue(book().getBestAsk().isEmpty());
    }

    @Test
    void fokOrderIgnoresLiquidityBeyondItsLimitPrice() {
        engine.submitOrder(order(OrderSide.SELL, "11.00", 100, TimeInForce.GTC));

        Order fok = order(OrderSide.BUY, "10.00", 100, TimeInForce.FOK);
        List<Trade> trades = engine.submitOrder(fok);

        assertTrue(trades.isEmpty());
        assertEquals(OrderStatus.CANCELLED, fok.getStatus());
        assertEquals(100, book().getBestAsk().orElseThrow().getRemainingQuantity());
    }

    @Test
    void gtcOrderRestsOnTheBookWhenNotFullyFilled() {
        Order gtc = order(OrderSide.BUY, "10.00", 100, TimeInForce.GTC);

        List<Trade> trades = engine.submitOrder(gtc);

        assertTrue(trades.isEmpty());
        assertEquals(OrderStatus.PENDING, gtc.getStatus());
        assertEquals(100, book().getBestBid().orElseThrow().getRemainingQuantity());
    }
}
