package io.github.pedrolucas167.lastro.metrics;

import java.math.BigDecimal;

public record TradingMetrics(
    int tradeCount,
    int totalVolume,
    BigDecimal vwap,
    BigDecimal averagePrice,
    int orderCount
) {
    @Override
    public String toString() {
        return String.format(
            "TradingMetrics{trades=%d, volume=%d, VWAP=%s, avgPrice=%s, orders=%d}",
            tradeCount, totalVolume, vwap, averagePrice, orderCount
        );
    }
}
