package io.github.pedrolucas167.lastro.domain;

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
    private volatile BigDecimal price;
    private volatile BigDecimal originalQuantity;
    private final AtomicInteger remainingQuantity;
    private final AtomicReference<OrderStatus> status;
    private final Instant createdAt;
    private final String accountId;
    private final TimeInForce timeInForce;
    private final Lock lock = new ReentrantLock();

    public Order(String id, String symbol, OrderSide side, OrderType type,
                 BigDecimal price, BigDecimal quantity, String accountId, TimeInForce timeInForce) {
        this.id = id;
        this.symbol = symbol;
        this.side = side;
        this.type = type;
        this.price = price;
        this.originalQuantity = quantity;
        this.remainingQuantity = new AtomicInteger(quantity.intValue());
        this.status = new AtomicReference<>(OrderStatus.PENDING);
        this.createdAt = Instant.now();
        this.accountId = accountId;
        this.timeInForce = timeInForce;
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

    /** Uma ordem aberta ainda pode executar -- e por isso ainda garante lastro. */
    public boolean isOpen() {
        return status.get() == OrderStatus.PENDING || status.get() == OrderStatus.PARTIALLY_FILLED;
    }

    /** Quantidade que ficaria em aberto se a ordem fosse alterada para essa quantidade. */
    public int remainingAfterAmend(int newQuantity) {
        return newQuantity - filledQuantity();
    }

    private int filledQuantity() {
        return originalQuantity.intValue() - remainingQuantity.get();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getAccountId() {
        return accountId;
    }

    public TimeInForce getTimeInForce() {
        return timeInForce;
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

    /**
     * Substitui preco e quantidade de uma ordem ainda em aberto, preservando o que ja foi
     * executado. Retorna false se a ordem ja foi finalizada ou se a nova quantidade for
     * menor que a quantidade executada.
     */
    public boolean amend(BigDecimal newPrice, BigDecimal newQuantity) {
        lock.lock();
        try {
            if (status.get() == OrderStatus.FILLED
                || status.get() == OrderStatus.CANCELLED
                || status.get() == OrderStatus.REJECTED) {
                return false;
            }

            int newRemaining = remainingAfterAmend(newQuantity.intValue());
            if (newRemaining < 0) {
                return false;
            }

            this.price = newPrice;
            this.originalQuantity = newQuantity;
            this.remainingQuantity.set(newRemaining);
            this.status.set(statusAfterAmend(newRemaining, filledQuantity()));
            return true;
        } finally {
            lock.unlock();
        }
    }

    private OrderStatus statusAfterAmend(int remainingQuantity, int filledQuantity) {
        if (remainingQuantity == 0) {
            return OrderStatus.FILLED;
        }
        return filledQuantity > 0 ? OrderStatus.PARTIALLY_FILLED : OrderStatus.PENDING;
    }
}
