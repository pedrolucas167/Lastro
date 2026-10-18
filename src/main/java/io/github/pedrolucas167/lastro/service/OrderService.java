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
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class OrderService {
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final Map<String, Order> ordersByIdempotencyKey = new ConcurrentHashMap<>();
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
        return createOrderWithIdempotencyKey(actor, symbol, side, type, price, quantity, accountId,
            timeInForce, null).order();
    }

    /**
     * Cria a ordem e reivindica a chave de idempotencia na mesma operacao: dois retries
     * simultaneos com a mesma chave produzem uma ordem so e um evento so no journal, e o
     * segundo recebe a ordem que ja existe.
     *
     * `created=false` quer dizer exatamente isso: a ordem ja era conhecida e ja foi ao engine,
     * entao quem chamou nao pode rematchar.
     */
    public Creation createOrderWithIdempotencyKey(Actor actor, String symbol, OrderSide side,
                                                 OrderType type, BigDecimal price, int quantity,
                                                 String accountId, TimeInForce timeInForce,
                                                 String idempotencyKey) {
        if (isBlank(idempotencyKey)) {
            return new Creation(newOrder(actor, symbol, side, type, price, quantity, accountId, timeInForce, null),
                true);
        }

        AtomicBoolean created = new AtomicBoolean();
        Order order = ordersByIdempotencyKey.computeIfAbsent(idempotencyKey, key -> {
            created.set(true);
            return newOrder(actor, symbol, side, type, price, quantity, accountId, timeInForce, key);
        });
        return new Creation(order, created.get());
    }

    /** A ordem aceita com essa chave, se houver: e ela que um retry recebe. */
    public Order getOrderWithIdempotencyKey(String idempotencyKey) {
        return isBlank(idempotencyKey) ? null : ordersByIdempotencyKey.get(idempotencyKey);
    }

    /** Recria uma ordem a partir do journal, sem regravar o evento. A chave volta com ela. */
    public Order register(String orderId, String symbol, OrderSide side, OrderType type,
                          BigDecimal price, int quantity, String accountId, TimeInForce timeInForce,
                          String idempotencyKey) {
        Order order = hold(orderId, symbol, side, type, price, quantity, accountId, timeInForce);
        if (!isBlank(idempotencyKey)) {
            ordersByIdempotencyKey.put(idempotencyKey, order);
        }
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
        ordersByIdempotencyKey.clear();
    }

    /** Ordem criada agora, ou a que a chave de idempotencia ja tinha (`created` diz qual). */
    public record Creation(Order order, boolean created) {
    }

    private Order newOrder(Actor actor, String symbol, OrderSide side, OrderType type, BigDecimal price,
                           int quantity, String accountId, TimeInForce timeInForce, String idempotencyKey) {
        String orderId = UUID.randomUUID().toString();
        eventStore.append(new OrderSubmitted(orderId, symbol, side, type, price, quantity, accountId,
            timeInForce, idempotencyKey, actor, Instant.now()));

        // O indice por chave fica com quem chamou: o computeIfAbsent guarda o que esta criacao
        // devolve, e registrar a chave aqui dentro seria atualizar o mapa durante o calculo dele.
        Order order = hold(orderId, symbol, side, type, price, quantity, accountId, timeInForce);

        logger.info("Created order {}: {} {} {} {} @ {} ({}) for account {}",
            orderId, side, type, quantity, symbol, price, timeInForce, accountId);

        return order;
    }

    private Order hold(String orderId, String symbol, OrderSide side, OrderType type, BigDecimal price,
                       int quantity, String accountId, TimeInForce timeInForce) {
        Order order = new Order(orderId, symbol, side, type, price, new BigDecimal(quantity),
            accountId, timeInForce);
        orders.put(orderId, order);
        return order;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
