package io.github.pedrolucas167.lastro.service;

import io.github.pedrolucas167.lastro.domain.*;
import io.github.pedrolucas167.lastro.journal.DomainEvent;
import io.github.pedrolucas167.lastro.journal.EventStore;
import io.github.pedrolucas167.lastro.journal.JournalRebuildable;
import io.github.pedrolucas167.lastro.journal.OrderCancelled;
import io.github.pedrolucas167.lastro.journal.OrderReplaced;
import io.github.pedrolucas167.lastro.journal.OrderSubmitted;
import io.github.pedrolucas167.lastro.journal.TradeExecuted;
import io.github.pedrolucas167.lastro.journal.TradingEvent;
import io.github.pedrolucas167.lastro.metrics.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class TradingService implements JournalRebuildable {
    private static final Logger logger = LoggerFactory.getLogger(TradingService.class);
    private final MatchingEngine matchingEngine;
    private final OrderService orderService;
    private final PositionService positionService;
    private final MetricsCollector metricsCollector;
    private final RegulatoryValidationService regulatoryValidationService;
    private final EventStore eventStore;

    public TradingService(MatchingEngine matchingEngine, OrderService orderService,
                         PositionService positionService, MetricsCollector metricsCollector,
                         RegulatoryValidationService regulatoryValidationService,
                         EventStore eventStore) {
        this.matchingEngine = matchingEngine;
        this.orderService = orderService;
        this.positionService = positionService;
        this.metricsCollector = metricsCollector;
        this.regulatoryValidationService = regulatoryValidationService;
        this.eventStore = eventStore;
    }

    public OrderExecutionResult submitOrder(String symbol, OrderSide side, OrderType type,
                                   BigDecimal price, int quantity, String accountId,
                                   TimeInForce timeInForce) {
        regulatoryValidationService.validateOrder(symbol, side, type, price, quantity, accountId);
        
        // O OrderService registra o OrderSubmitted no journal ao criar a ordem.
        Order order = orderService.createOrder(symbol, side, type, price, quantity, accountId, timeInForce);
        
        List<Trade> trades = matchingEngine.submitOrder(order);
        
        if (!trades.isEmpty()) {
            recordTrades(trades);
            logger.info("Order {} generated {} trades", order.getId(), trades.size());
        }
        
        return new OrderExecutionResult(order, trades);
    }

    public Optional<OrderExecutionResult> replaceOrder(String orderId, String symbol,
                                                      BigDecimal newPrice, Integer newQuantity) {
        Order order = orderService.getOrder(orderId);
        if (order == null) {
            return Optional.empty();
        }

        BigDecimal price = newPrice != null ? newPrice : order.getPrice();
        int quantity = newQuantity != null ? newQuantity : order.getOriginalQuantity().intValue();

        regulatoryValidationService.validateOrder(symbol, order.getSide(), order.getType(),
            price, quantity, order.getAccountId());

        BigDecimal amendedQuantity = new BigDecimal(quantity);
        eventStore.append(new OrderReplaced(orderId, symbol, price, amendedQuantity, Instant.now()));

        Optional<List<Trade>> generatedTrades =
            matchingEngine.replaceOrder(orderId, symbol, price, amendedQuantity);
        if (generatedTrades.isEmpty()) {
            return Optional.empty();
        }

        List<Trade> trades = generatedTrades.get();
        recordTrades(trades);
        logger.info("Order {} replaced and generated {} trades", orderId, trades.size());

        return Optional.of(new OrderExecutionResult(order, trades));
    }

    private void recordTrades(List<Trade> trades) {
        for (Trade trade : trades) {
            eventStore.append(new TradeExecuted(trade.getId(), trade.getSymbol(),
                trade.getBuyOrderId(), trade.getSellOrderId(), trade.getPrice(), trade.getQuantity(),
                trade.getBuyAccountId(), trade.getSellAccountId(), trade.getTimestamp()));
            positionService.processTrade(trade);
            metricsCollector.recordTrade(trade);
        }
    }

    public boolean cancelOrder(String orderId, String symbol) {
        eventStore.append(new OrderCancelled(orderId, symbol, Instant.now()));
        return matchingEngine.cancelOrder(orderId, symbol);
    }

    public OrderBook getOrderBook(String symbol) {
        return matchingEngine.getOrderBook(symbol);
    }

    public List<Trade> getTrades() {
        return matchingEngine.getTrades();
    }

    public List<Trade> getTradesForSymbol(String symbol) {
        return matchingEngine.getTradesForSymbol(symbol);
    }

    public int getPositionQuantity(String accountId, String symbol) {
        return positionService.getQuantity(accountId, symbol);
    }

    public BigDecimal getPositionAveragePrice(String accountId, String symbol) {
        return positionService.getAveragePrice(accountId, symbol);
    }

    public BigDecimal getPositionPnL(String accountId, String symbol, BigDecimal currentPrice) {
        return positionService.getPnL(accountId, symbol, currentPrice);
    }

    /**
     * Reconstroi o engine a partir do journal, reexecutando os comandos na mesma ordem.
     * Como o matching e deterministico, o book e as posicoes voltam ao mesmo estado; os
     * trades sao restaurados do journal (preservando id e timestamp).
     */
    public void rebuild(List<DomainEvent> events) {
        clear();
        List<Trade> journaledTrades = new ArrayList<>();

        for (DomainEvent event : events) {
            if (!(event instanceof TradingEvent tradingEvent)) {
                continue;
            }

            switch (tradingEvent) {
                case OrderSubmitted e -> matchingEngine.submitOrder(orderService.register(
                    e.orderId(), e.symbol(), e.side(), e.orderType(), e.price(), e.quantity(),
                    e.accountId(), e.timeInForce()));
                case OrderCancelled e -> matchingEngine.cancelOrder(e.orderId(), e.symbol());
                case OrderReplaced e -> matchingEngine.replaceOrder(e.orderId(), e.symbol(),
                    e.price(), e.quantity());
                case TradeExecuted e -> journaledTrades.add(new Trade(e.tradeId(), e.symbol(),
                    e.buyOrderId(), e.sellOrderId(), e.price(), e.quantity(), e.buyAccountId(),
                    e.sellAccountId(), e.occurredAt()));
            }
        }

        matchingEngine.replaceTrades(journaledTrades);
        journaledTrades.forEach(positionService::processTrade);
    }

    public void clear() {
        matchingEngine.clear();
        orderService.clear();
        positionService.clear();
    }
}
