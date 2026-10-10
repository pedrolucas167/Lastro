package org.example.lastro.domain;

import java.math.BigDecimal;
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
        lock.lock();
        try {
            int currentQty = quantity.get();
            BigDecimal currentAvg = averagePrice.get();
            
            if (currentQty == 0) {
                averagePrice.set(price);
            } else {
                BigDecimal totalCost = currentAvg.multiply(new BigDecimal(currentQty))
                    .add(price.multiply(new BigDecimal(qty)));
                int newQty = currentQty + qty;
                averagePrice.set(totalCost.divide(new BigDecimal(newQty), 2, BigDecimal.ROUND_HALF_UP));
            }
            quantity.addAndGet(qty);
        } finally {
            lock.unlock();
        }
    }

    public void addSell(int qty, BigDecimal price) {
        lock.lock();
        try {
            if (quantity.get() < qty) {
                throw new IllegalArgumentException("Insufficient position to sell");
            }
            quantity.addAndGet(-qty);
            
            if (quantity.get() == 0) {
                averagePrice.set(BigDecimal.ZERO);
            }
        } finally {
            lock.unlock();
        }
    }
}
