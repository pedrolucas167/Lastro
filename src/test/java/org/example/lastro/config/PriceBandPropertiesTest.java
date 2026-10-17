package org.example.lastro.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PriceBandPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(TestConfig.class)
        .withPropertyValues(
            "lastro.market.price-band.enabled=true",
            "lastro.market.price-band.percent=10",
            "lastro.market.price-band.reference-prices[PETR4]=30.50");

    @Test
    void bindsReferencePricesMap() {
        runner.run(context -> {
            PriceBandProperties properties = context.getBean(PriceBandProperties.class);

            BigDecimal reference = properties.getReferencePrices().get("PETR4");
            assertNotNull(reference);
            assertEquals(0, new BigDecimal("30.50").compareTo(reference));
            assertEquals(0, new BigDecimal("10").compareTo(properties.getPercent()));
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PriceBandProperties.class)
    static class TestConfig {
    }
}
