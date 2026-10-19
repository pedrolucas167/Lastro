package io.github.pedrolucas167.lastro.service;

import io.github.pedrolucas167.lastro.config.PriceBandProperties;
import io.github.pedrolucas167.lastro.domain.Actor;
import io.github.pedrolucas167.lastro.domain.MatchingEngine;
import io.github.pedrolucas167.lastro.domain.Order;
import io.github.pedrolucas167.lastro.domain.OrderSide;
import io.github.pedrolucas167.lastro.domain.OrderStatus;
import io.github.pedrolucas167.lastro.domain.OrderType;
import io.github.pedrolucas167.lastro.domain.TimeInForce;
import io.github.pedrolucas167.lastro.journal.DomainEvent;
import io.github.pedrolucas167.lastro.journal.EventStore;
import io.github.pedrolucas167.lastro.journal.InMemoryEventStore;
import io.github.pedrolucas167.lastro.journal.OrderCancelled;
import io.github.pedrolucas167.lastro.journal.OrderReplaced;
import io.github.pedrolucas167.lastro.metrics.MetricsCollector;
import io.github.pedrolucas167.lastro.service.TradingService.CancelOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O journal so afirma o que aconteceu: comando que o engine recusou nao vira evento, e cancelar
 * de novo e reenvio -- nao um fato novo atribuido a quem so repetiu o pedido.
 */
class OrderCancellationTest {
    private static final Actor BACK_OFFICE = new Actor("operador", Set.of(Actor.OPERATOR));
    private static final String SYMBOL = "BBDC4";

    private EventStore eventStore;
    private TradingService tradingService;

    @BeforeEach
    void setUp() {
        eventStore = new InMemoryEventStore();
        tradingService = newTradingService(eventStore);
    }

    @Test
    @DisplayName("Cancelar ordem que nao existe responde desconhecida e nao grava nada")
    void cancellingAnUnknownOrderWritesNothing() {
        assertEquals(CancelOutcome.UNKNOWN,
            tradingService.cancelOrder(BACK_OFFICE, "ordem-inexistente", SYMBOL));

        assertEquals(0, journaled(OrderCancelled.class));
    }

    @Test
    @DisplayName("Cancelar no simbolo errado nao cancela a ordem nem grava")
    void cancellingWithTheWrongSymbolWritesNothing() {
        Order order = submit(OrderSide.BUY, "30.00", 100);

        assertEquals(CancelOutcome.UNKNOWN,
            tradingService.cancelOrder(BACK_OFFICE, order.getId(), "WEGE3"));

        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals(0, journaled(OrderCancelled.class));
    }

    @Test
    @DisplayName("Cancelar de novo e reenvio: responde ja cancelada e nao grava fato novo")
    void cancellingTwiceWritesOneFact() {
        Order order = submit(OrderSide.BUY, "30.00", 100);

        assertEquals(CancelOutcome.CANCELLED,
            tradingService.cancelOrder(BACK_OFFICE, order.getId(), SYMBOL));
        assertEquals(CancelOutcome.ALREADY_CANCELLED,
            tradingService.cancelOrder(BACK_OFFICE, order.getId(), SYMBOL));

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(1, journaled(OrderCancelled.class));
    }

    @Test
    @DisplayName("Ordem preenchida nao aceita cancelamento, e isso nao vira fato")
    void filledOrderIsNotCancellable() {
        Order buy = submit(OrderSide.BUY, "30.00", 100);
        submit(OrderSide.SELL, "30.00", 100);

        assertEquals(OrderStatus.FILLED, buy.getStatus());
        assertEquals(CancelOutcome.NOT_CANCELLABLE,
            tradingService.cancelOrder(BACK_OFFICE, buy.getId(), SYMBOL));

        assertEquals(0, journaled(OrderCancelled.class));
    }

    @Test
    @DisplayName("Amend recusado pelo engine nao vira OrderReplaced e deixa a ordem no book")
    void refusedAmendWritesNothing() {
        Order buy = submit(OrderSide.BUY, "30.00", 100);
        submit(OrderSide.SELL, "30.00", 40);

        assertEquals(OrderStatus.PARTIALLY_FILLED, buy.getStatus());

        // Quantidade abaixo do que ja foi executado: o engine recusa, entao nao houve amend.
        assertTrue(tradingService.replaceOrder(BACK_OFFICE, buy.getId(), SYMBOL,
            new BigDecimal("30.00"), 20).isEmpty());

        assertEquals(0, journaled(OrderReplaced.class));
        assertEquals(0, new BigDecimal("30.00")
            .compareTo(tradingService.getOrderBook(SYMBOL).getBestBid().orElseThrow().getPrice()));
    }

    @Test
    @DisplayName("Amend aceito grava o fato e o replay devolve o mesmo book")
    void acceptedAmendIsJournaled() {
        Order buy = submit(OrderSide.BUY, "30.00", 100);
        submit(OrderSide.SELL, "40.00", 100);

        assertTrue(tradingService.replaceOrder(BACK_OFFICE, buy.getId(), SYMBOL,
            new BigDecimal("29.00"), null).isPresent());

        assertEquals(1, journaled(OrderReplaced.class));

        TradingService replayed = newTradingService(eventStore);
        replayed.rebuild(eventStore.readAll());

        assertEquals(0, new BigDecimal("29.00")
            .compareTo(replayed.getOrderBook(SYMBOL).getBestBid().orElseThrow().getPrice()));
        assertEquals(0, new BigDecimal("40.00")
            .compareTo(replayed.getOrderBook(SYMBOL).getBestAsk().orElseThrow().getPrice()));
    }

    @Test
    @DisplayName("Depois do replay o cancelamento repetido continua sendo um fato so")
    void replayKeepsOneCancellationFact() {
        Order order = submit(OrderSide.BUY, "30.00", 100);
        tradingService.cancelOrder(BACK_OFFICE, order.getId(), SYMBOL);
        tradingService.cancelOrder(BACK_OFFICE, order.getId(), SYMBOL);

        TradingService replayed = newTradingService(eventStore);
        replayed.rebuild(eventStore.readAll());

        assertEquals(1, journaled(OrderCancelled.class));
        assertTrue(replayed.getOrderBook(SYMBOL).getBestBid().isEmpty());
    }

    private Order submit(OrderSide side, String price, int quantity) {
        return tradingService.submitOrder(BACK_OFFICE, SYMBOL, side, OrderType.LIMIT,
            new BigDecimal(price), quantity, "conta-" + side, TimeInForce.GTC).order();
    }

    private long journaled(Class<? extends DomainEvent> type) {
        return eventStore.readAll().stream().filter(type::isInstance).count();
    }

    private TradingService newTradingService(EventStore eventStore) {
        RegulatoryValidationService validation = new RegulatoryValidationService(
            new MarketHaltService(eventStore), new PriceBandProperties(), false, true, "00:00", "23:59");
        return new TradingService(new MatchingEngine(), new OrderService(eventStore),
            new PositionService(), new MetricsCollector(), validation, eventStore);
    }
}
