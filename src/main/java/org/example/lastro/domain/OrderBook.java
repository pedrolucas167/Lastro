package org.example.lastro.domain;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class OrderBook {
    private final String symbol;
    private final ConcurrentSkipListMap<BigDecimal, LinkedList<Order>> bids;
    private final ConcurrentSkipListMap<BigDecimal, LinkedList<Order>> asks;
    private final Lock lock = new ReentrantLock();

    public OrderBook(String symbol) {
        this.symbol = symbol;
        this.bids = new ConcurrentSkipListMap<>(Collections.reverseOrder());
        this.asks = new ConcurrentSkipListMap<>();
    }

    public String getSymbol() {
        return symbol;
    }

    public void addOrder(Order order) {
        lock.lock();
        try {
            ConcurrentSkipListMap<BigDecimal, LinkedList<Order>> book = 
                order.getSide() == OrderSide.BUY ? bids : asks;
            
            book.computeIfAbsent(order.getPrice(), k -> new LinkedList<>()).addLast(order);
        } finally {
            lock.unlock();
        }
    }

    public void removeOrder(Order order) {
        lock.lock();
        try {
            ConcurrentSkipListMap<BigDecimal, LinkedList<Order>> book = 
                order.getSide() == OrderSide.BUY ? bids : asks;
            
            LinkedList<Order> orders = book.get(order.getPrice());
            if (orders != null) {
                orders.remove(order);
                if (orders.isEmpty()) {
                    book.remove(order.getPrice());
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public Optional<Order> removeById(String orderId) {
        lock.lock();
        try {
            return removeByIdFrom(bids, orderId).or(() -> removeByIdFrom(asks, orderId));
        } finally {
            lock.unlock();
        }
    }

    private Optional<Order> removeByIdFrom(ConcurrentSkipListMap<BigDecimal, LinkedList<Order>> book,
                                           String orderId) {
        for (Map.Entry<BigDecimal, LinkedList<Order>> entry : book.entrySet()) {
            LinkedList<Order> orders = entry.getValue();
            Iterator<Order> iterator = orders.iterator();
            while (iterator.hasNext()) {
                Order order = iterator.next();
                if (order.getId().equals(orderId)) {
                    iterator.remove();
                    if (orders.isEmpty()) {
                        book.remove(entry.getKey());
                    }
                    return Optional.of(order);
                }
            }
        }
        return Optional.empty();
    }

    public Optional<Order> getBestBid() {
        lock.lock();
        try {
            Map.Entry<BigDecimal, LinkedList<Order>> entry = bids.firstEntry();
            if (entry == null || entry.getValue().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(entry.getValue().getFirst());
        } finally {
            lock.unlock();
        }
    }

    public Optional<Order> getBestAsk() {
        lock.lock();
        try {
            Map.Entry<BigDecimal, LinkedList<Order>> entry = asks.firstEntry();
            if (entry == null || entry.getValue().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(entry.getValue().getFirst());
        } finally {
            lock.unlock();
        }
    }

    public List<Order> getBidsAtPrice(BigDecimal price) {
        lock.lock();
        try {
            LinkedList<Order> orders = bids.get(price);
            return orders != null ? new ArrayList<>(orders) : Collections.emptyList();
        } finally {
            lock.unlock();
        }
    }

    public List<Order> getAsksAtPrice(BigDecimal price) {
        lock.lock();
        try {
            LinkedList<Order> orders = asks.get(price);
            return orders != null ? new ArrayList<>(orders) : Collections.emptyList();
        } finally {
            lock.unlock();
        }
    }

    public BigDecimal getSpread() {
        Optional<Order> bestBid = getBestBid();
        Optional<Order> bestAsk = getBestAsk();
        
        if (bestBid.isPresent() && bestAsk.isPresent()) {
            return bestAsk.get().getPrice().subtract(bestBid.get().getPrice());
        }
        return null;
    }

    public int getTotalBidQuantity() {
        lock.lock();
        try {
            return bids.values().stream()
                .flatMap(List::stream)
                .mapToInt(Order::getRemainingQuantity)
                .sum();
        } finally {
            lock.unlock();
        }
    }

    public int getTotalAskQuantity() {
        lock.lock();
        try {
            return asks.values().stream()
                .flatMap(List::stream)
                .mapToInt(Order::getRemainingQuantity)
                .sum();
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        lock.lock();
        try {
            bids.clear();
            asks.clear();
        } finally {
            lock.unlock();
        }
    }
}
