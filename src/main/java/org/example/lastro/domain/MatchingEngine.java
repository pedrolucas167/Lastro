package org.example.lastro.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MatchingEngine {
    private static final Logger logger = LoggerFactory.getLogger(MatchingEngine.class);

    private final ConcurrentHashMap<String, OrderBook> orderBooks;
    private final ConcurrentHashMap<String, Lock> symbolLocks;
    private final ConcurrentLinkedQueue<Trade> trades;

    public MatchingEngine() {
        this.orderBooks = new ConcurrentHashMap<>();
        this.symbolLocks = new ConcurrentHashMap<>();
        this.trades = new ConcurrentLinkedQueue<>();
    }

    public OrderBook getOrderBook(String symbol) {
        return orderBooks.computeIfAbsent(symbol, OrderBook::new);
    }

    private Lock lockForSymbol(String symbol) {
        return symbolLocks.computeIfAbsent(symbol, s -> new ReentrantLock());
    }

    public List<Trade> submitOrder(Order order) {
        Lock lock = lockForSymbol(order.getSymbol());
        lock.lock();
        try {
            OrderBook orderBook = getOrderBook(order.getSymbol());
            List<Trade> generatedTrades = new ArrayList<>();

            if (order.getTimeInForce() == TimeInForce.FOK && !isFullyFillable(order, orderBook)) {
                order.cancel();
                logger.info("FOK order {} cancelled: liquidity insufficient for a full fill",
                    order.getId());
                return generatedTrades;
            }

            match(order, orderBook, generatedTrades);

            if (order.getRemainingQuantity() > 0) {
                if (restsOnBook(order)) {
                    orderBook.addOrder(order);
                    logger.info("Order {} added to book with remaining quantity {}",
                        order.getId(), order.getRemainingQuantity());
                } else {
                    order.cancel();
                    logger.info("Order {} cancelled with remaining quantity {}",
                        order.getId(), order.getRemainingQuantity());
                }
            } else {
                logger.info("Order {} fully filled", order.getId());
            }

            if (!generatedTrades.isEmpty()) {
                trades.addAll(generatedTrades);
            }
            return generatedTrades;
        } finally {
            lock.unlock();
        }
    }

    private boolean restsOnBook(Order order) {
        return order.getTimeInForce() == TimeInForce.GTC;
    }

    private boolean isFullyFillable(Order incoming, OrderBook orderBook) {
        boolean isBuy = incoming.getSide() == OrderSide.BUY;
        int fillableQuantity = 0;

        for (Order resting : orderBook.getOppositeSideSnapshot(incoming.getSide())) {
            if (!crosses(incoming, resting, isBuy)) {
                break;
            }
            fillableQuantity += resting.getRemainingQuantity();
            if (fillableQuantity >= incoming.getRemainingQuantity()) {
                return true;
            }
        }

        return false;
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
            quantity,
            buyOrder.getAccountId(),
            sellOrder.getAccountId()
        );

        trades.add(trade);
        logger.info("Trade executed: {} {} @ {}", quantity, buyOrder.getSymbol(), price);
    }

    public boolean cancelOrder(String orderId, String symbol) {
        Lock lock = lockForSymbol(symbol);
        lock.lock();
        try {
            OrderBook orderBook = orderBooks.get(symbol);
            if (orderBook == null) {
                return false;
            }

            return orderBook.removeById(orderId)
                .map(order -> {
                    order.cancel();
                    return true;
                })
                .orElse(false);
        } finally {
            lock.unlock();
        }
    }

    public List<Trade> getTrades() {
        return new ArrayList<>(trades);
    }

    public List<Trade> getTradesForSymbol(String symbol) {
        return trades.stream()
            .filter(t -> t.getSymbol().equals(symbol))
            .toList();
    }

    public void clear() {
        orderBooks.clear();
        symbolLocks.clear();
        trades.clear();
    }
}
