package io.github.pedrolucas167.lastro.service;

import io.github.pedrolucas167.lastro.config.PriceBandProperties;
import io.github.pedrolucas167.lastro.domain.OrderSide;
import io.github.pedrolucas167.lastro.domain.OrderType;
import io.github.pedrolucas167.lastro.exception.RegulatoryException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegulatoryValidationServiceTest {
    private static final String SYMBOL = "PETR4";

    private PriceBandProperties priceBand(BigDecimal reference) {
        PriceBandProperties properties = new PriceBandProperties();
        properties.setReferencePrices(Map.of(SYMBOL, reference));
        return properties;
    }

    private RegulatoryValidationService service(PriceBandProperties properties) {
        return new RegulatoryValidationService(properties, false, true, "00:00", "23:59");
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
}
