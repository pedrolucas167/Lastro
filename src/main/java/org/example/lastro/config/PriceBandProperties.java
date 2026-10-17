package org.example.lastro.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

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
}
