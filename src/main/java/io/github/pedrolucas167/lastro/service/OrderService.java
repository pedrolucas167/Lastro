package io.github.pedrolucas167.lastro.service;

import io.github.pedrolucas167.lastro.domain.Actor;
import io.github.pedrolucas167.lastro.domain.Order;
import io.github.pedrolucas167.lastro.domain.OrderSide;
import io.github.pedrolucas167.lastro.domain.OrderStatus;
import io.github.pedrolucas167.lastro.domain.OrderType;
import io.github.pedrolucas167.lastro.domain.TimeInForce;
import io.github.pedrolucas167.lastro.journal.EventStore;
import io.github.pedrolucas167.lastro.journal.OrderSubmitted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderService {
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final EventStore eventStore;

    public OrderService(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    public Order createOrder(Actor actor, String symbol, OrderSide side, OrderType type,
                            BigDecimal price, int quantity, String accountId) {
        return createOrder(actor, symbol, side, type, price, quantity, accountId, TimeInForce.GTC);
    }

    public Order createOrder(Actor actor, String symbol, OrderSide side, OrderType type,
                            BigDecimal price, int quantity, String accountId, TimeInForce timeInForce) {
        String orderId = UUID.randomUUID().toString();
        eventStore.append(new OrderSubmitted(orderId, symbol, side, type, price, quantity,
            accountId, timeInForce, actor, Instant.now()));

        Order order = register(orderId, symbol, side, type, price, quantity, accountId, timeInForce);

        logger.info("Created order {}: {} {} {} {} @ {} ({}) for account {}",
            orderId, side, type, quantity, symbol, price, timeInForce, accountId);

        return order;
    }

    /** Recria uma ordem a partir do journal, sem regravar o evento. */
    public Order register(String orderId, String symbol, OrderSide side, OrderType type,
                          BigDecimal price, int quantity, String accountId, TimeInForce timeInForce) {
        Order order = new Order(orderId, symbol, side, type, price, new BigDecimal(quantity),
            accountId, timeInForce);
        orders.put(orderId, order);
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
