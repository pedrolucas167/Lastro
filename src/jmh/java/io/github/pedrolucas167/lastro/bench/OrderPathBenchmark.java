package io.github.pedrolucas167.lastro.bench;

import io.github.pedrolucas167.lastro.domain.Actor;
import io.github.pedrolucas167.lastro.domain.Order;
import io.github.pedrolucas167.lastro.domain.OrderSide;
import io.github.pedrolucas167.lastro.domain.OrderType;
import io.github.pedrolucas167.lastro.domain.TimeInForce;
import io.github.pedrolucas167.lastro.journal.EventStore;
import io.github.pedrolucas167.lastro.journal.InMemoryEventStore;
import io.github.pedrolucas167.lastro.journal.OrderSubmitted;
import io.github.pedrolucas167.lastro.service.OrderService;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Custo do caminho de aplicacao, comparado com o custo do motor isolado.
 *
 * journalAppend mede so a escrita no journal; createOrder mede o caminho da API
 * inteiro: UUID, append no journal, registro na tabela de ordens e logging
 * (desligado na medicao). A diferenca entre os dois e o preco do resto.
 *
 * Single-thread de proposito: e o custo por operacao, nao o throughput agregado.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@Threads(1)
public class OrderPathBenchmark {

    private static final BigDecimal PRICE = new BigDecimal("30.00");
    private static final Actor BENCH_ACTOR = new Actor("benchmark", Set.of(Actor.OPERATOR));

    @State(Scope.Benchmark)
    public static class PathState {
        private EventStore eventStore;
        private OrderService orderService;
        private long sequence;

        @Setup(Level.Iteration)
        public void reset() {
            eventStore = new InMemoryEventStore();
            orderService = new OrderService(eventStore);
            sequence = 0;
        }
    }

    @Benchmark
    public OrderSubmitted journalAppend(PathState state) {
        OrderSubmitted event = new OrderSubmitted("o" + state.sequence++, "PETR4", OrderSide.BUY,
            OrderType.LIMIT, PRICE, 10, "acc", TimeInForce.GTC, null, BENCH_ACTOR, Instant.now());
        state.eventStore.append(event);
        return event;
    }

    @Benchmark
    public Order createOrder(PathState state) {
        return state.orderService.createOrder(BENCH_ACTOR, "PETR4", OrderSide.BUY, OrderType.LIMIT, PRICE, 10,
            "acc-" + state.sequence++);
    }
}
