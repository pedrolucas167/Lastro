package io.github.pedrolucas167.lastro.concurrency;

import io.github.pedrolucas167.lastro.domain.*;
import io.github.pedrolucas167.lastro.journal.InMemoryEventStore;
import io.github.pedrolucas167.lastro.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class TradingConcurrencyTest {
    private static final Actor BACK_OFFICE = new Actor("operador", Set.of(Actor.OPERATOR));

    private MatchingEngine matchingEngine;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        matchingEngine = new MatchingEngine();
        orderService = new OrderService(new InMemoryEventStore());
    }

    @Test
    void testConcurrentBuyOrdersAtSamePrice() throws InterruptedException {
        String symbol = "PETR4";
        int numOrders = 100;
        int threads = 10;
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < numOrders / threads; i++) {
                        Order order = orderService.createOrder(BACK_OFFICE, 
                            symbol, OrderSide.BUY, OrderType.LIMIT,
                            new BigDecimal("30.00"), 10, "acc-" + Thread.currentThread().getId()
                        );
                        matchingEngine.submitOrder(order);
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(numOrders, successCount.get());
        
        OrderBook orderBook = matchingEngine.getOrderBook(symbol);
        assertTrue(orderBook.getTotalBidQuantity() > 0);
    }

    @Test
    void testConcurrentMatchingOfBuyAndSellOrders() throws InterruptedException {
        String symbol = "VALE3";
        int numPairs = 50;
        int threads = 10;
        CountDownLatch latch = new CountDownLatch(threads * 2);
        AtomicInteger tradeCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threads * 2);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < numPairs / threads; i++) {
                        Order buyOrder = orderService.createOrder(BACK_OFFICE, 
                            symbol, OrderSide.BUY, OrderType.LIMIT,
                            new BigDecimal("50.00"), 10, "acc-buy-" + threadId
                        );
                        matchingEngine.submitOrder(buyOrder);
                    }
                } finally {
                    latch.countDown();
                }
            });

            executor.submit(() -> {
                try {
                    for (int i = 0; i < numPairs / threads; i++) {
                        Order sellOrder = orderService.createOrder(BACK_OFFICE, 
                            symbol, OrderSide.SELL, OrderType.LIMIT,
                            new BigDecimal("50.00"), 10, "acc-sell-" + threadId
                        );
                        List<Trade> trades = matchingEngine.submitOrder(sellOrder);
                        tradeCount.addAndGet(trades.size());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(tradeCount.get() > 0, "Should have generated trades");
        
        List<Trade> allTrades = matchingEngine.getTradesForSymbol(symbol);
        int totalTradedQuantity = allTrades.stream().mapToInt(Trade::getQuantity).sum();
        assertEquals(numPairs * 10, totalTradedQuantity);
    }

    @Test
    void testConcurrentOrderBookConsistency() throws InterruptedException {
        String symbol = "ITUB4";
        int numOrders = 200;
        int threads = 20;
        CountDownLatch latch = new CountDownLatch(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < numOrders / threads; i++) {
                        OrderSide side = (threadId % 2 == 0) ? OrderSide.BUY : OrderSide.SELL;
                        BigDecimal price = new BigDecimal(20 + (i % 10));
                        Order order = orderService.createOrder(BACK_OFFICE, 
                            symbol, side, OrderType.LIMIT,
                            price, 10, "acc-" + threadId
                        );
                        matchingEngine.submitOrder(order);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        OrderBook orderBook = matchingEngine.getOrderBook(symbol);
        
        int totalBidQty = orderBook.getTotalBidQuantity();
        int totalAskQty = orderBook.getTotalAskQuantity();
        
        assertTrue(totalBidQty > 0 || totalAskQty > 0);
        
        BigDecimal spread = orderBook.getSpread();
        if (spread != null) {
            assertTrue(spread.compareTo(BigDecimal.ZERO) >= 0);
        }
    }

    @Test
    void testNoDeadlockInConcurrentTrading() throws InterruptedException {
        String symbol = "BBDC4";
        int numCycles = 100;
        int threads = 10;
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger completedCycles = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < numCycles; i++) {
                        Order buy = orderService.createOrder(BACK_OFFICE, 
                            symbol, OrderSide.BUY, OrderType.LIMIT,
                            new BigDecimal("50.00"), 5, "acc-" + threadId
                        );
                        matchingEngine.submitOrder(buy);
                        
                        Order sell = orderService.createOrder(BACK_OFFICE, 
                            symbol, OrderSide.SELL, OrderType.LIMIT,
                            new BigDecimal("50.00"), 5, "acc-" + threadId
                        );
                        matchingEngine.submitOrder(sell);
                        
                        completedCycles.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(60, TimeUnit.SECONDS), "Should complete without deadlock");
        executor.shutdown();

        assertEquals(threads * numCycles, completedCycles.get());
    }

    @Test
    void testPositionConsistencyUnderConcurrentTrades() throws InterruptedException {
        String symbol = "WEGE3";
        int numTrades = 100;
        int threads = 10;
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger positionUpdates = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < numTrades / threads; i++) {
                        Order buy = orderService.createOrder(BACK_OFFICE, 
                            symbol, OrderSide.BUY, OrderType.LIMIT,
                            new BigDecimal("40.00"), 10, "acc-" + threadId
                        );
                        matchingEngine.submitOrder(buy);
                        
                        Order sell = orderService.createOrder(BACK_OFFICE, 
                            symbol, OrderSide.SELL, OrderType.LIMIT,
                            new BigDecimal("40.00"), 10, "acc-" + threadId
                        );
                        matchingEngine.submitOrder(sell);
                        
                        positionUpdates.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threads * numTrades / threads, positionUpdates.get());
        
        List<Trade> trades = matchingEngine.getTradesForSymbol(symbol);
        int totalTraded = trades.stream().mapToInt(Trade::getQuantity).sum();
        
        assertTrue(totalTraded > 0);
    }
}
