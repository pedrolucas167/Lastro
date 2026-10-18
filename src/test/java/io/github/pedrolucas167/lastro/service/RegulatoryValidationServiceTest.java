package io.github.pedrolucas167.lastro.service;

import io.github.pedrolucas167.lastro.config.PriceBandProperties;
import io.github.pedrolucas167.lastro.domain.Actor;
import io.github.pedrolucas167.lastro.domain.OrderSide;
import io.github.pedrolucas167.lastro.domain.OrderType;
import io.github.pedrolucas167.lastro.exception.RegulatoryException;
import io.github.pedrolucas167.lastro.journal.InMemoryEventStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegulatoryValidationServiceTest {
    private static final String SYMBOL = "PETR4";
    private static final Actor BACK_OFFICE = new Actor("operador", Set.of(Actor.OPERATOR));

    private PriceBandProperties priceBand(BigDecimal reference) {
        PriceBandProperties properties = new PriceBandProperties();
        properties.setReferencePrices(Map.of(SYMBOL, reference));
        return properties;
    }

    private RegulatoryValidationService service(PriceBandProperties properties) {
        return service(properties, new MarketHaltService(new InMemoryEventStore()));
    }

    private RegulatoryValidationService service(PriceBandProperties properties, MarketHaltService marketHalt) {
        return new RegulatoryValidationService(marketHalt, properties, false, true, "00:00", "23:59");
    }

    @Test
    void acceptsPriceInsideTheBand() {
        RegulatoryValidationService service = service(priceBand(new BigDecimal("30.50")));

        assertDoesNotThrow(() -> service.validateOrder(
            SYMBOL, OrderSide.BUY, OrderType.LIMIT, new BigDecimal("33.00"), 100, "acc-1"));
    }

    @Test
    void rejectsPriceAboveTheUpperBand() {
        RegulatoryValidationService service = service(priceBand(new BigDecimal("30.50")));

        assertThrows(RegulatoryException.class, () -> service.validateOrder(
            SYMBOL, OrderSide.BUY, OrderType.LIMIT, new BigDecimal("40.00"), 100, "acc-1"));
    }

    @Test
    void rejectsPriceBelowTheLowerBand() {
        RegulatoryValidationService service = service(priceBand(new BigDecimal("30.50")));

        assertThrows(RegulatoryException.class, () -> service.validateOrder(
            SYMBOL, OrderSide.SELL, OrderType.LIMIT, new BigDecimal("20.00"), 100, "acc-1"));
    }

    @Test
    void acceptsAnyPriceForSymbolWithoutReference() {
        RegulatoryValidationService service = service(priceBand(new BigDecimal("30.50")));

        assertDoesNotThrow(() -> service.validateOrder(
            "VALE3", OrderSide.BUY, OrderType.LIMIT, new BigDecimal("200.00"), 100, "acc-1"));
    }

    @Test
    void ignoresBandWhenDisabled() {
        PriceBandProperties properties = priceBand(new BigDecimal("30.50"));
        properties.setEnabled(false);

        assertDoesNotThrow(() -> service(properties).validateOrder(
            SYMBOL, OrderSide.BUY, OrderType.LIMIT, new BigDecimal("40.00"), 100, "acc-1"));
    }

    @Test
    void ignoresBandForMarketOrders() {
        RegulatoryValidationService service = service(priceBand(new BigDecimal("30.50")));

        assertDoesNotThrow(() -> service.validateOrder(
            SYMBOL, OrderSide.BUY, OrderType.MARKET, null, 100, "acc-1"));
    }

    @Test
    @DisplayName("Símbolo pausado rejeita ordem nova, com o motivo do halt")
    void rejectsOrderForHaltedSymbol() {
        MarketHaltService marketHalt = new MarketHaltService(new InMemoryEventStore());
        marketHalt.halt(BACK_OFFICE, SYMBOL, "circuito de protecao");
        RegulatoryValidationService service = service(priceBand(new BigDecimal("30.50")), marketHalt);

        RegulatoryException rejected = assertThrows(RegulatoryException.class, () -> service.validateOrder(
            SYMBOL, OrderSide.BUY, OrderType.LIMIT, new BigDecimal("30.50"), 100, "acc-1"));

        assertTrue(rejected.getMessage().contains("circuito de protecao"));
    }

    @Test
    @DisplayName("Depois do resume o símbolo volta a aceitar ordem")
    void acceptsOrderAfterResume() {
        MarketHaltService marketHalt = new MarketHaltService(new InMemoryEventStore());
        marketHalt.halt(BACK_OFFICE, SYMBOL, "circuito");
        marketHalt.resume(BACK_OFFICE, SYMBOL);
        RegulatoryValidationService service = service(priceBand(new BigDecimal("30.50")), marketHalt);

        assertDoesNotThrow(() -> service.validateOrder(
            SYMBOL, OrderSide.BUY, OrderType.LIMIT, new BigDecimal("30.50"), 100, "acc-1"));
    }

    @Test
    @DisplayName("O halt de um símbolo não afeta outro")
    void haltOfOneSymbolDoesNotAffectAnother() {
        MarketHaltService marketHalt = new MarketHaltService(new InMemoryEventStore());
        marketHalt.halt(BACK_OFFICE, SYMBOL, "circuito");
        RegulatoryValidationService service = service(priceBand(new BigDecimal("30.50")), marketHalt);

        assertDoesNotThrow(() -> service.validateOrder(
            "VALE3", OrderSide.BUY, OrderType.LIMIT, new BigDecimal("200.00"), 100, "acc-1"));
    }
}
