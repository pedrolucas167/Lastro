package io.github.pedrolucas167.lastro.domain;

import java.math.BigDecimal;
import java.time.Instant;

public class Trade {
    private final String id;
    private final String symbol;
    private final String buyOrderId;
    private final String sellOrderId;
    private final String buyAccountId;
    private final String sellAccountId;
    private final BigDecimal price;
    private final int quantity;
    private final Instant timestamp;

    public Trade(String id, String symbol, String buyOrderId, String sellOrderId, 
                 BigDecimal price, int quantity, String buyAccountId, String sellAccountId) {
        this(id, symbol, buyOrderId, sellOrderId, price, quantity, buyAccountId, sellAccountId,
            Instant.now());
    }

    public Trade(String id, String symbol, String buyOrderId, String sellOrderId,
                 BigDecimal price, int quantity, String buyAccountId, String sellAccountId,
                 Instant timestamp) {
        this.id = id;
        this.symbol = symbol;
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.buyAccountId = buyAccountId;
        this.sellAccountId = sellAccountId;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = timestamp;
    }

    public String getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getBuyOrderId() {
        return buyOrderId;
    }

    public String getSellOrderId() {
        return sellOrderId;
    }

    public String getBuyAccountId() {
        return buyAccountId;
    }

    public String getSellAccountId() {
        return sellAccountId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantity;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
