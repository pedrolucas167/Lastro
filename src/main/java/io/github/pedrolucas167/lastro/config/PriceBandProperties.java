package io.github.pedrolucas167.lastro.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Bandas de preco (limit up / limit down). Cada simbolo tem um preco de referencia
 * (ex.: fechamento anterior) e ordens fora de +/- {@code percent} dele sao rejeitadas.
 * Simbolos sem referencia configurada nao tem banda.
 */
@Data
@ConfigurationProperties(prefix = "lastro.market.price-band")
public class PriceBandProperties {
    private boolean enabled = true;
    private BigDecimal percent = new BigDecimal("10");
    private Map<String, BigDecimal> referencePrices = new HashMap<>();

    /** Piso da banda (referencia - percent); vazio quando nao ha banda para o simbolo. */
    public Optional<BigDecimal> lowerBound(String symbol) {
        return bound(symbol, BigDecimal.ONE.subtract(factor()));
    }

    /** Teto da banda (referencia + percent); vazio quando nao ha banda para o simbolo. */
    public Optional<BigDecimal> upperBound(String symbol) {
        return bound(symbol, BigDecimal.ONE.add(factor()));
    }

    private BigDecimal factor() {
        return percent.movePointLeft(2);
    }

    private Optional<BigDecimal> bound(String symbol, BigDecimal factor) {
        if (!enabled) {
            return Optional.empty();
        }
        BigDecimal reference = referencePrices.get(symbol);
        return reference == null ? Optional.empty() : Optional.of(reference.multiply(factor));
    }
}
