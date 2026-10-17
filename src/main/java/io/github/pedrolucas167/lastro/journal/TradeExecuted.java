package io.github.pedrolucas167.lastro.journal;

import java.math.BigDecimal;
import java.time.Instant;

/** Fato: duas ordens casaram e geraram um trade. */
public record TradeExecuted(String tradeId, String symbol, String buyOrderId, String sellOrderId,
                            BigDecimal price, int quantity, String buyAccountId, String sellAccountId,
                            Instant occurredAt)
        implements TradingEvent {

    @Override
    public String type() {
        return "TradeExecuted";
    }
}
