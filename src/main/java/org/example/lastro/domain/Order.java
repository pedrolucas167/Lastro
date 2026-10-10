package org.example.lastro.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Order {
    private final String id;
    private final String symbol;
    private final OrderSide side;
    private final OrderType type;
    private final BigDecimal price;
    private final BigDecimal originalQuantity;
    private final AtomicInteger remainingQuantity;
    private final AtomicReference<OrderStatus> status;
    private final Instant createdAt;
    private final Lock lock = new ReentrantLock();

    public Order(String id, String symbol, OrderSide side, OrderType type, 
                 BigDecimal price, BigDecimal quantity) {
        this.id = id;
        this.symbol = symbol;
        this.side = side;
        this.type = type;
        this.price = price;
        this.originalQuantity = quantity;
        this.remainingQuantity = new AtomicInteger(quantity.intValue());
        this.status = new AtomicReference<>(OrderStatus.PENDING);
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public OrderSide getSide() {
        return side;
    }

    public OrderType getType() {
        return type;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getOriginalQuantity() {
        return originalQuantity;
    }

    public int getRemainingQuantity() {
        return remainingQuantity.get();
    }

    public OrderStatus getStatus() {
        return status.get();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean fill(int quantity) {
        lock.lock();
        try {
            if (remainingQuantity.get() < quantity) {
                return false;
            }
            remainingQuantity.addAndGet(-quantity);
            if (remainingQuantity.get() == 0) {
                status.set(OrderStatus.FILLED);
            } else {
                status.set(OrderStatus.PARTIALLY_FILLED);
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    public boolean cancel() {
        lock.lock();
        try {
            if (status.get() == OrderStatus.FILLED || status.get() == OrderStatus.CANCELLED) {
                return false;
            }
            status.set(OrderStatus.CANCELLED);
            return true;
        } finally {
            lock.unlock();
        }
    }

    public boolean reject(String reason) {
        lock.lock();
        try {
            if (status.get() != OrderStatus.PENDING) {
                return false;
            }
            status.set(OrderStatus.REJECTED);
            return true;
        } finally {
            lock.unlock();
        }
    }
}
