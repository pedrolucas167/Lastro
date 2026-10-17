package org.example.lastro.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MarketHaltServiceTest {
    private final MarketHaltService service = new MarketHaltService();

    @Test
    void haltsAndResumesSymbol() {
        assertFalse(service.isHalted("PETR4"));

        service.halt("PETR4", "circuit breaker");

        assertTrue(service.isHalted("PETR4"));
        assertEquals("circuit breaker", service.haltReason("PETR4"));
        assertEquals(Map.of("PETR4", "circuit breaker"), service.haltedSymbols());

        service.resume("PETR4");

        assertFalse(service.isHalted("PETR4"));
        assertTrue(service.haltedSymbols().isEmpty());
    }

    @Test
    void usesDefaultReasonWhenNoneGiven() {
        service.halt("PETR4", "   ");

        assertEquals("no reason given", service.haltReason("PETR4"));
    }

    @Test
    void haltIsScopedToSymbol() {
        service.halt("PETR4", "circuit breaker");

        assertFalse(service.isHalted("VALE3"));
    }
}
