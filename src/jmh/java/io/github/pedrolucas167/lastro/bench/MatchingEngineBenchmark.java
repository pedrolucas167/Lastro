package io.github.pedrolucas167.lastro.bench;

import io.github.pedrolucas167.lastro.domain.MatchingEngine;
import io.github.pedrolucas167.lastro.domain.Order;
import io.github.pedrolucas167.lastro.domain.OrderSide;
import io.github.pedrolucas167.lastro.domain.OrderType;
import io.github.pedrolucas167.lastro.domain.TimeInForce;
import io.github.pedrolucas167.lastro.domain.Trade;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Throughput do matching engine, sem journal e sem logging.
 *
 * Cada invocacao envia duas ordens (BUY e SELL) que cruzam no mesmo preco. Isso
 * mantem o book vazio ao fim de cada invocacao, evitando que o benchmark meca
 * crescimento de memoria em vez de matching. @OperationsPerInvocation(2) faz o
 * JMH reportar ordens/segundo, e nao pares/segundo.
 *
 * O parametro symbols controla a contencao: com 1 simbolo todas as threads
 * disputam o mesmo lock de simbolo; com 8, cada thread tem o seu.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class MatchingEngineBenchmark {

    private static final BigDecimal PRICE = new BigDecimal("30.00");
    private static final BigDecimal QUANTITY = new BigDecimal("10");

    @State(Scope.Benchmark)
    public static class EngineState {
        MatchingEngine engine;

        @Setup(Level.Iteration)
        public void reset() {
            engine = new MatchingEngine();
        }
    }

    @State(Scope.Thread)
    public static class TraderState {
        @Param({"1", "8"})
        int symbols;

        private static final AtomicInteger NEXT_TRADER = new AtomicInteger();

        private String symbol;
        private long sequence;

        @Setup(Level.Iteration)
        public void assignSymbol() {
            symbol = "BENCH" + (NEXT_TRADER.getAndIncrement() % symbols);
            sequence = 0;
        }

        private Order order(OrderSide side) {
            return new Order(side.name().charAt(0) + symbol + "-" + sequence++, symbol, side,
                OrderType.LIMIT, PRICE, QUANTITY, "acc-" + symbol, TimeInForce.GTC);
        }
    }

    @Benchmark
    @OperationsPerInvocation(2)
    public List<Trade> matchedPair(EngineState engineState, TraderState trader) {
        engineState.engine.submitOrder(trader.order(OrderSide.BUY));
        return engineState.engine.submitOrder(trader.order(OrderSide.SELL));
    }
}
