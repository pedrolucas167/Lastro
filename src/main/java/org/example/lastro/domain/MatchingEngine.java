package org.example.lastro.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MatchingEngine {
    private static final Logger logger = LoggerFactory.getLogger(MatchingEngine.class);
    
    private final ConcurrentHashMap<String, OrderBook> orderBooks;
    private final List<Trade> trades;
    private final Lock lock = new ReentrantLock();

    public MatchingEngine() {
        this.orderBooks = new ConcurrentHashMap<>();
        this.trades = new ArrayList<>();
    }

    public OrderBook getOrderBook(String symbol) {
        return orderBooks.computeIfAbsent(symbol, OrderBook::new);
    }

    public List<Trade> submitOrder(Order order) {
        lock.lock();
        try {
            OrderBook orderBook = getOrderBook(order.getSymbol());
            List<Trade> generatedTrades = new ArrayList<>();

            match(order, orderBook, generatedTrades);

            if (order.getRemainingQuantity() > 0) {
                orderBook.addOrder(order);
                logger.info("Order {} added to book with remaining quantity {}", 
                    order.getId(), order.getRemainingQuantity());
            } else {
                logger.info("Order {} fully filled", order.getId());
            }

            trades.addAll(generatedTrades);
            return generatedTrades;
        } finally {
            lock.unlock();
        }
    }

    private void match(Order incoming, OrderBook orderBook, List<Trade> trades) {
        boolean isBuy = incoming.getSide() == OrderSide.BUY;

        while (incoming.getRemainingQuantity() > 0) {
            var bestOpposite = isBuy ? orderBook.getBestAsk() : orderBook.getBestBid();

            if (bestOpposite.isEmpty()) {
                break;
            }

            Order resting = bestOpposite.get();

            if (!crosses(incoming, resting, isBuy)) {
                break;
            }

            int matchQuantity = Math.min(incoming.getRemainingQuantity(),
                                        resting.getRemainingQuantity());

            executeTrade(
                isBuy ? incoming : resting,
                isBuy ? resting : incoming,
                matchQuantity,
                resting.getPrice(),
                trades
            );

            if (resting.getRemainingQuantity() == 0) {
                orderBook.removeOrder(resting);
            }
        }
    }

    private boolean crosses(Order incoming, Order resting, boolean isBuy) {
        if (incoming.getType() == OrderType.MARKET) {
            return true;
        }
        int comparison = incoming.getPrice().compareTo(resting.getPrice());
        return isBuy ? comparison >= 0 : comparison <= 0;
    }

    private void executeTrade(Order buyOrder, Order sellOrder, int quantity, 
                             BigDecimal price, List<Trade> trades) {
        buyOrder.fill(quantity);
        sellOrder.fill(quantity);

        Trade trade = new Trade(
            UUID.randomUUID().toString(),
            buyOrder.getSymbol(),
            buyOrder.getId(),
            sellOrder.getId(),
            price,
            quantity
        );

        trades.add(trade);
        logger.info("Trade executed: {} {} @ {}", quantity, buyOrder.getSymbol(), price);
    }

    public boolean cancelOrder(String orderId, String symbol) {
        lock.lock();
        try {
            OrderBook orderBook = orderBooks.get(symbol);
            if (orderBook == null) {
                return false;
            }

            return orderBook.getBidsAtPrice(BigDecimal.ZERO).stream()
                .filter(o -> o.getId().equals(orderId))
                .findFirst()
                .map(order -> {
                    order.cancel();
                    orderBook.removeOrder(order);
                    return true;
                })
                .orElseGet(() -> 
                    orderBook.getAsksAtPrice(BigDecimal.ZERO).stream()
                        .filter(o -> o.getId().equals(orderId))
                        .findFirst()
                        .map(order -> {
                            order.cancel();
                            orderBook.removeOrder(order);
                            return true;
                        })
                        .orElse(false)
                );
        } finally {
            lock.unlock();
        }
    }

    public List<Trade> getTrades() {
        lock.lock();
        try {
            return new ArrayList<>(trades);
        } finally {
            lock.unlock();
        }
    }

    public List<Trade> getTradesForSymbol(String symbol) {
        lock.lock();
        try {
            return trades.stream()
                .filter(t -> t.getSymbol().equals(symbol))
                .toList();
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        lock.lock();
        try {
            orderBooks.clear();
            trades.clear();
        } finally {
            lock.unlock();
        }
    }
}
