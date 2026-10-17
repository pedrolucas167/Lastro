package io.github.pedrolucas167.lastro.concurrency;

import io.github.pedrolucas167.lastro.domain.MatchingEngine;
import io.github.pedrolucas167.lastro.domain.Order;
import io.github.pedrolucas167.lastro.domain.OrderSide;
import io.github.pedrolucas167.lastro.domain.OrderType;
import io.github.pedrolucas167.lastro.journal.InMemoryEventStore;
import io.github.pedrolucas167.lastro.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

// Estes testes verificam que todas as ordens completam sob carga concorrente.
// O tempo impresso e um sinal de fumaca, nao um benchmark: ele varia com o
// estado da JVM (JIT, GC) e com o escalonador do sistema operacional, e o mesmo
// cenario ja variou de 3,9s a 10,2s entre execucoes. Por isso o limite de
// conclusao e o timeout do latch, nao uma assertiva de tempo. Medir desempenho
// exige um instrumento proprio (JMH), nao o relogio deste teste.
@Tag("slow")
class ScaleTest {
    private MatchingEngine matchingEngine;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        matchingEngine = new MatchingEngine();
        orderService = new OrderService(new InMemoryEventStore());
    }

    @Test
    @DisplayName("Teste de escala: 10 threads, 200 ordens")
    void testScale10Threads100Orders() throws InterruptedException {
        runScenario(10, 10);
    }

    @Test
    @DisplayName("Teste de escala: 50 threads, 1000 ordens")
    void testScale50Threads500Orders() throws InterruptedException {
        runScenario(50, 10);
    }

    @Test
    @DisplayName("Teste de escala: 100 threads, 2000 ordens")
    void testScale100Threads1000Orders() throws InterruptedException {
        runScenario(100, 10);
    }

    @Test
    @DisplayName("Teste de escala: 200 threads, 4000 ordens")
    void testScale200Threads2000Orders() throws InterruptedException {
        runScenario(200, 10);
    }

    @Test
    @DisplayName("Teste de escala com muitas contas: 100 contas, 2000 ordens")
    void testScaleWithManyAccounts() throws InterruptedException {
        runScenarioWithManyAccounts(100, 10);
    }

    @Test
    @DisplayName("Teste de escala com muitas contas: 1000 contas, 20000 ordens")
    void testScaleWithVeryManyAccounts() throws InterruptedException {
        runScenarioWithManyAccounts(1_000, 10);
    }

    private void runScenario(int numThreads, int pairsPerThread) throws InterruptedException {
        long elapsed = runConcurrentOrders(numThreads, pairsPerThread);
        report(numThreads + " threads", numThreads * pairsPerThread * 2, elapsed);
    }

    private void runScenarioWithManyAccounts(int numAccounts, int pairsPerAccount) throws InterruptedException {
        long elapsed = runConcurrentOrdersWithManyAccounts(numAccounts, pairsPerAccount);
        report(numAccounts + " contas", numAccounts * pairsPerAccount * 2, elapsed);
    }

    private static void report(String scenario, int orders, long elapsedNanos) {
        double throughput = orders / (elapsedNanos / 1_000_000_000.0);
        System.out.println("=== Escala: " + scenario + ", " + orders + " ordens ===");
        System.out.println("Tempo total: " + TimeUnit.NANOSECONDS.toMillis(elapsedNanos) + "ms");
        System.out.println("Throughput: " + String.format("%.2f", throughput) + " ordens/seg");
    }

    // Mede apenas a janela de processamento: criacao do pool e shutdown ficam
    // fora, senao o tempo medido e dominado pela orquestracao e nao pela carga.
    private long runConcurrentOrders(int numThreads, int pairsPerThread) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger completedOrders = new AtomicInteger(0);

        long start = System.nanoTime();
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < pairsPerThread; j++) {
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
        long elapsed = System.nanoTime() - start;

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals(numThreads * pairsPerThread * 2, completedOrders.get());
        return elapsed;
    }

    private long runConcurrentOrdersWithManyAccounts(int numAccounts, int pairsPerAccount)
            throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(numAccounts, 100));
        CountDownLatch latch = new CountDownLatch(numAccounts);
        AtomicInteger completedOrders = new AtomicInteger(0);

        long start = System.nanoTime();
        for (int i = 0; i < numAccounts; i++) {
            final int accountId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < pairsPerAccount; j++) {
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
        long elapsed = System.nanoTime() - start;

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals(numAccounts * pairsPerAccount * 2, completedOrders.get());
        return elapsed;
    }
}
