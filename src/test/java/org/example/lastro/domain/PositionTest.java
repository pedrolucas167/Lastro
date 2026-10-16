package org.example.lastro.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PositionTest {
    private Position position;

    @BeforeEach
    void setUp() {
        position = new Position("acc-1", "PETR4");
    }

    @Test
    @DisplayName("Deve calcular preço médio ponderado ao acumular compras")
    void shouldComputeWeightedAverageOnBuys() {
        position.addBuy(100, new BigDecimal("30.00"));
        position.addBuy(50, new BigDecimal("33.00"));

        assertEquals(150, position.getQuantity());
        assertEquals(0, position.getAveragePrice().compareTo(new BigDecimal("31.00")));
    }

    @Test
    @DisplayName("Deve manter preço médio ao reduzir parcialmente a posição")
    void shouldKeepAverageOnPartialSell() {
        position.addBuy(100, new BigDecimal("30.00"));
        position.addSell(40, new BigDecimal("35.00"));

        assertEquals(60, position.getQuantity());
        assertEquals(0, position.getAveragePrice().compareTo(new BigDecimal("30.00")));
    }

    @Test
    @DisplayName("Deve zerar preço médio ao fechar a posição")
    void shouldResetAverageWhenFlat() {
        position.addBuy(100, new BigDecimal("30.00"));
        position.addSell(100, new BigDecimal("35.00"));

        assertEquals(0, position.getQuantity());
        assertEquals(0, position.getAveragePrice().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Deve permitir posição vendida sem lançar exceção")
    void shouldAllowShortPosition() {
        position.addSell(40, new BigDecimal("30.50"));

        assertEquals(-40, position.getQuantity());
        assertEquals(0, position.getAveragePrice().compareTo(new BigDecimal("30.50")));
    }

    @Test
    @DisplayName("Deve abrir posição comprada ao cruzar o zero partindo de vendida")
    void shouldCrossZeroFromShortToLong() {
        position.addSell(50, new BigDecimal("30.00"));
        position.addBuy(80, new BigDecimal("32.00"));

        assertEquals(30, position.getQuantity());
        assertEquals(0, position.getAveragePrice().compareTo(new BigDecimal("32.00")));
    }

    @Test
    @DisplayName("Deve manter preço médio ao cobrir parcialmente posição vendida")
    void shouldKeepAverageWhenCoveringShort() {
        position.addSell(100, new BigDecimal("30.00"));
        position.addBuy(40, new BigDecimal("33.00"));

        assertEquals(-60, position.getQuantity());
        assertEquals(0, position.getAveragePrice().compareTo(new BigDecimal("30.00")));
    }
}
