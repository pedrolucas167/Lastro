package io.github.pedrolucas167.lastro.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Position {
    private final String accountId;
    private final String symbol;
    private final AtomicInteger quantity;
    private final AtomicReference<BigDecimal> averagePrice;
    private final Lock lock = new ReentrantLock();

    public Position(String accountId, String symbol) {
        this.accountId = accountId;
        this.symbol = symbol;
        this.quantity = new AtomicInteger(0);
        this.averagePrice = new AtomicReference<>(BigDecimal.ZERO);
    }

    public String getAccountId() {
        return accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public int getQuantity() {
        return quantity.get();
    }

    public BigDecimal getAveragePrice() {
        return averagePrice.get();
    }

    public void addBuy(int qty, BigDecimal price) {
        applyTrade(qty, price);
    }

    public void addSell(int qty, BigDecimal price) {
        applyTrade(-qty, price);
    }

    private void applyTrade(int signedQuantity, BigDecimal price) {
        lock.lock();
        try {
            int current = quantity.get();
            int updated = current + signedQuantity;

            if (updated == 0) {
                averagePrice.set(BigDecimal.ZERO);
            } else if (opensOrReversesPosition(current, updated)) {
                averagePrice.set(price);
            } else if (growsInSameDirection(current, updated)) {
                averagePrice.set(weightedAverage(current, updated, signedQuantity, price));
            }

            quantity.set(updated);
        } finally {
            lock.unlock();
        }
    }

    private static boolean opensOrReversesPosition(int current, int updated) {
        return current == 0 || Integer.signum(current) != Integer.signum(updated);
    }

    private static boolean growsInSameDirection(int current, int updated) {
        return Math.abs(updated) > Math.abs(current);
    }

    private BigDecimal weightedAverage(int current, int updated, int signedQuantity, BigDecimal price) {
        BigDecimal currentCost = averagePrice.get().multiply(BigDecimal.valueOf(Math.abs(current)));
        BigDecimal addedCost = price.multiply(BigDecimal.valueOf(Math.abs(signedQuantity)));
        return currentCost.add(addedCost)
            .divide(BigDecimal.valueOf(Math.abs(updated)), 2, RoundingMode.HALF_UP);
    }
}
