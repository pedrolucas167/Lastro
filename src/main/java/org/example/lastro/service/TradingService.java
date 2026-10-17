package org.example.lastro.service;

import org.example.lastro.domain.*;
import org.example.lastro.metrics.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class TradingService {
    private static final Logger logger = LoggerFactory.getLogger(TradingService.class);
    private final MatchingEngine matchingEngine;
    private final OrderService orderService;
    private final PositionService positionService;
    private final MetricsCollector metricsCollector;
    private final RegulatoryValidationService regulatoryValidationService;

    public TradingService(MatchingEngine matchingEngine, OrderService orderService,
                         PositionService positionService, MetricsCollector metricsCollector,
                         RegulatoryValidationService regulatoryValidationService) {
        this.matchingEngine = matchingEngine;
        this.orderService = orderService;
        this.positionService = positionService;
        this.metricsCollector = metricsCollector;
        this.regulatoryValidationService = regulatoryValidationService;
    }

    public OrderExecutionResult submitOrder(String symbol, OrderSide side, OrderType type,
                                   BigDecimal price, int quantity, String accountId,
                                   TimeInForce timeInForce) {
        regulatoryValidationService.validateOrder(symbol, side, type, price, quantity, accountId);
        
        Order order = orderService.createOrder(symbol, side, type, price, quantity, accountId, timeInForce);
        
        List<Trade> trades = matchingEngine.submitOrder(order);
        
        if (!trades.isEmpty()) {
            for (Trade trade : trades) {
                positionService.processTrade(trade);
                metricsCollector.recordTrade(trade);
            }
            logger.info("Order {} generated {} trades", order.getId(), trades.size());
        }
        
        return new OrderExecutionResult(order, trades);
    }

    public boolean cancelOrder(String orderId, String symbol) {
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

    public void clear() {
        matchingEngine.clear();
        orderService.clear();
        positionService.clear();
    }
}
