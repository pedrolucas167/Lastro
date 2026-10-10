package org.example.lastro.service;

import org.example.lastro.domain.Order;
import org.example.lastro.domain.OrderSide;
import org.example.lastro.domain.OrderStatus;
import org.example.lastro.domain.OrderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderService {
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    private final Map<String, Order> orders = new ConcurrentHashMap<>();

    public Order createOrder(String symbol, OrderSide side, OrderType type, 
                            BigDecimal price, int quantity, String accountId) {
        String orderId = UUID.randomUUID().toString();
        Order order = new Order(orderId, symbol, side, type, price, new BigDecimal(quantity));
        orders.put(orderId, order);
        
        logger.info("Created order {}: {} {} {} {} @ {} for account {}", 
            orderId, side, type, quantity, symbol, price, accountId);
        
        return order;
    }

    public Order getOrder(String orderId) {
        return orders.get(orderId);
    }

    public boolean cancelOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order == null) {
            return false;
        }
        
        boolean cancelled = order.cancel();
        if (cancelled) {
            logger.info("Cancelled order {}", orderId);
        }
        return cancelled;
    }

    public void updateOrderStatus(String orderId, OrderStatus status) {
        Order order = orders.get(orderId);
        if (order != null) {
            logger.info("Order {} status updated to {}", orderId, status);
        }
    }

    public void clear() {
        orders.clear();
    }
}
