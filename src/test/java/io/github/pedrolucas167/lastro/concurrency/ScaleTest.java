package io.github.pedrolucas167.lastro.concurrency;

import io.github.pedrolucas167.lastro.domain.MatchingEngine;
import io.github.pedrolucas167.lastro.domain.Order;
import io.github.pedrolucas167.lastro.domain.OrderSide;
import io.github.pedrolucas167.lastro.domain.OrderType;
import io.github.pedrolucas167.lastro.domain.Position;
import io.github.pedrolucas167.lastro.journal.InMemoryEventStore;
import io.github.pedrolucas167.lastro.service.OrderService;
import io.github.pedrolucas167.lastro.service.PositionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class ScaleTest {
    private MatchingEngine matchingEngine;
    private OrderService orderService;
    private PositionService positionService;

    @BeforeEach
    void setUp() {
        matchingEngine = new MatchingEngine();
        orderService = new OrderService(new InMemoryEventStore());
    }

    @Test
    @DisplayName("Teste de escala: 10 threads, 100 ordens")
    void testScale10Threads100Orders() throws InterruptedException {
        int numThreads = 10;
        int ordersPerThread = 10;
        int totalOrders = numThreads * ordersPerThread;
        
        long startTime = System.currentTimeMillis();
        runConcurrentOrders(numThreads, ordersPerThread);
        long endTime = System.currentTimeMillis();
        
        long duration = endTime - startTime;
        double throughput = (double) totalOrders / (duration / 1000.0);
        
        System.out.println("=== Escala: 10 threads, 100 ordens ===");
        System.out.println("Tempo total: " + duration + "ms");
        System.out.println("Throughput: " + String.format("%.2f", throughput) + " ordens/seg");
        
        assertTrue(duration < 5000, "Deve completar em menos de 5 segundos");
        assertTrue(throughput > 10, "Throughput deve ser maior que 10 ordens/seg");
    }

    @Test
    @DisplayName("Teste de escala: 50 threads, 500 ordens")
    void testScale50Threads500Orders() throws InterruptedException {
        int numThreads = 50;
        int ordersPerThread = 10;
        int totalOrders = numThreads * ordersPerThread;
        
        long startTime = System.currentTimeMillis();
        runConcurrentOrders(numThreads, ordersPerThread);
        long endTime = System.currentTimeMillis();
        
        long duration = endTime - startTime;
        double throughput = (double) totalOrders / (duration / 1000.0);
        
        System.out.println("=== Escala: 50 threads, 500 ordens ===");
        System.out.println("Tempo total: " + duration + "ms");
        System.out.println("Throughput: " + String.format("%.2f", throughput) + " ordens/seg");
        
        assertTrue(duration < 10000, "Deve completar em menos de 10 segundos");
        assertTrue(throughput > 20, "Throughput deve ser maior que 20 ordens/seg");
    }

    @Test
    @DisplayName("Teste de escala: 100 threads, 1000 ordens")
    void testScale100Threads1000Orders() throws InterruptedException {
        int numThreads = 100;
        int ordersPerThread = 10;
        int totalOrders = numThreads * ordersPerThread;
        
        long startTime = System.currentTimeMillis();
        runConcurrentOrders(numThreads, ordersPerThread);
        long endTime = System.currentTimeMillis();
        
        long duration = endTime - startTime;
        double throughput = (double) totalOrders / (duration / 1000.0);
        
        System.out.println("=== Escala: 100 threads, 1000 ordens ===");
        System.out.println("Tempo total: " + duration + "ms");
        System.out.println("Throughput: " + String.format("%.2f", throughput) + " ordens/seg");
        
        assertTrue(duration < 20000, "Deve completar em menos de 20 segundos");
    }

    @Test
    @DisplayName("Teste de escala: 200 threads, 2000 ordens")
    void testScale200Threads2000Orders() throws InterruptedException {
        int numThreads = 200;
        int ordersPerThread = 10;
        int totalOrders = numThreads * ordersPerThread;
        
        long startTime = System.currentTimeMillis();
        runConcurrentOrders(numThreads, ordersPerThread);
        long endTime = System.currentTimeMillis();
        
        long duration = endTime - startTime;
        double throughput = (double) totalOrders / (duration / 1000.0);
        
        System.out.println("=== Escala: 200 threads, 2000 ordens ===");
        System.out.println("Tempo total: " + duration + "ms");
        System.out.println("Throughput: " + String.format("%.2f", throughput) + " ordens/seg");
        
        // Este teste identifica o limite de escala
        assertTrue(duration < 30000, "Deve completar em menos de 30 segundos");
    }

    @Test
    @DisplayName("Teste de escala com muitas contas: 100 contas, 1000 ordens")
    void testScaleWithManyAccounts() throws InterruptedException {
        int numAccounts = 100;
        int ordersPerAccount = 10;
        int totalOrders = numAccounts * ordersPerAccount;
        
        long startTime = System.currentTimeMillis();
        runConcurrentOrdersWithManyAccounts(numAccounts, ordersPerAccount);
        long endTime = System.currentTimeMillis();
        
        long duration = endTime - startTime;
        double throughput = (double) totalOrders / (duration / 1000.0);
        
        System.out.println("=== Escala: 100 contas, 1000 ordens ===");
        System.out.println("Tempo total: " + duration + "ms");
        System.out.println("Throughput: " + String.format("%.2f", throughput) + " ordens/seg");
        
        assertTrue(duration < 15000, "Deve completar em menos de 15 segundos");
    }

    @Test
    @DisplayName("Teste de escala com muitas contas: 1000 contas, 10000 ordens")
    void testScaleWithVeryManyAccounts() throws InterruptedException {
        int numAccounts = 1000;
        int ordersPerAccount = 10;
        int totalOrders = numAccounts * ordersPerAccount;
        
        long startTime = System.currentTimeMillis();
        runConcurrentOrdersWithManyAccounts(numAccounts, ordersPerAccount);
        long endTime = System.currentTimeMillis();
        
        long duration = endTime - startTime;
        double throughput = (double) totalOrders / (duration / 1000.0);
        
        System.out.println("=== Escala: 1000 contas, 10000 ordens ===");
        System.out.println("Tempo total: " + duration + "ms");
        System.out.println("Throughput: " + String.format("%.2f", throughput) + " ordens/seg");
        
        // Este teste identifica gargalo com muitas contas
        assertTrue(duration < 60000, "Deve completar em menos de 60 segundos");
    }

    private void runConcurrentOrders(int numThreads, int ordersPerThread) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger completedOrders = new AtomicInteger(0);
        
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < ordersPerThread; j++) {
                        String symbol = "PETR4";
                        Order buy = orderService.createOrder(
                            symbol, OrderSide.BUY, OrderType.LIMIT,
                            new BigDecimal("30.00"), 10, "acc-" + threadId
                        );
                        matchingEngine.submitOrder(buy);
                        
                        Order sell = orderService.createOrder(
                            symbol, OrderSide.SELL, OrderType.LIMIT,
                            new BigDecimal("30.00"), 10, "acc-" + threadId
                        );
                        matchingEngine.submitOrder(sell);
                        
                        completedOrders.addAndGet(2);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(60, TimeUnit.SECONDS), "Teste deve completar em 60 segundos");
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        
        assertEquals(numThreads * ordersPerThread * 2, completedOrders.get());
    }

    private void runConcurrentOrdersWithManyAccounts(int numAccounts, int ordersPerAccount) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(numAccounts, 100));
        CountDownLatch latch = new CountDownLatch(numAccounts);
        AtomicInteger completedOrders = new AtomicInteger(0);
        
        for (int i = 0; i < numAccounts; i++) {
            final int accountId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < ordersPerAccount; j++) {
                        String symbol = "VALE3";
                        Order buy = orderService.createOrder(
                            symbol, OrderSide.BUY, OrderType.LIMIT,
                            new BigDecimal("50.00"), 10, "acc-" + accountId
                        );
                        matchingEngine.submitOrder(buy);
                        
                        Order sell = orderService.createOrder(
                            symbol, OrderSide.SELL, OrderType.LIMIT,
                            new BigDecimal("50.00"), 10, "acc-" + accountId
                        );
                        matchingEngine.submitOrder(sell);
                        
                        completedOrders.addAndGet(2);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(120, TimeUnit.SECONDS), "Teste deve completar em 120 segundos");
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        
        assertEquals(numAccounts * ordersPerAccount * 2, completedOrders.get());
    }
}
