package io.github.pedrolucas167.lastro.service;

import io.github.pedrolucas167.lastro.domain.*;
import io.github.pedrolucas167.lastro.exception.OrderIdempotencyKeyReusedException;
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

    public OrderExecutionResult submitOrder(Actor actor, String symbol, OrderSide side, OrderType type,
                                   BigDecimal price, int quantity, String accountId,
                                   TimeInForce timeInForce) {
        return submitOrder(actor, symbol, side, type, price, quantity, accountId, timeInForce, null);
    }

    /**
     * Envia a ordem. Com chave de idempotencia, um retry do cliente devolve a ordem que ja foi
     * aceita, sem ordem nova e sem evento novo no journal.
     */
    public OrderExecutionResult submitOrder(Actor actor, String symbol, OrderSide side, OrderType type,
                                   BigDecimal price, int quantity, String accountId,
                                   TimeInForce timeInForce, String idempotencyKey) {
        Order retried = orderService.getOrderWithIdempotencyKey(idempotencyKey);
        if (retried != null) {
            // Retry da mesma chave: a ordem ja foi aceita e ja foi ao engine. Revalidar aqui
            // poderia recusa-la por uma regra que mudou depois -- e o retry nao pode mudar a
            // resposta que o cliente ja tinha recebido.
            return retryOf(retried, symbol, side, type, timeInForce, accountId);
        }

        regulatoryValidationService.validateOrder(symbol, side, type, price, quantity, accountId);

        // O OrderService registra o OrderSubmitted no journal ao criar a ordem.
        OrderService.Creation creation = orderService.createOrderWithIdempotencyKey(actor, symbol, side,
            type, price, quantity, accountId, timeInForce, idempotencyKey);

        if (!creation.created()) {
            // Corrida: outra chamada com a mesma chave criou a ordem entre a consulta e aqui.
            return retryOf(creation.order(), symbol, side, type, timeInForce, accountId);
        }

        Order order = creation.order();
        List<Trade> trades = matchingEngine.submitOrder(order);

        if (!trades.isEmpty()) {
            recordTrades(actor, trades);
            logger.info("Order {} generated {} trades", order.getId(), trades.size());
        }

        return new OrderExecutionResult(order, trades);
    }

    /**
     * A ordem que a chave de idempotencia ja tinha. Chave repetida para outro comando e erro do
     * cliente, nao retry -- mas preco e quantidade ficam de fora da comparacao porque o amend os
     * move: quem alterou a ordem e reenvia o pedido original continua sendo um retry.
     */
    private OrderExecutionResult retryOf(Order known, String symbol, OrderSide side, OrderType type,
                                        TimeInForce timeInForce, String accountId) {
        boolean sameCommand = known.getSymbol().equals(symbol)
            && known.getSide() == side
            && known.getType() == type
            && known.getTimeInForce() == timeInForce
            && known.getAccountId().equals(accountId);
        if (!sameCommand) {
            throw new OrderIdempotencyKeyReusedException(known.getId());
        }
        return new OrderExecutionResult(known, List.of());
    }

    public Optional<OrderExecutionResult> replaceOrder(Actor actor, String orderId, String symbol,
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

        Optional<List<Trade>> generatedTrades =
            matchingEngine.replaceOrder(orderId, symbol, price, amendedQuantity);
        if (generatedTrades.isEmpty()) {
            // O engine recusou (fora de repouso ou amend invalido): nada aconteceu, entao nada
            // vai para o journal.
            return Optional.empty();
        }

        eventStore.append(new OrderReplaced(orderId, symbol, price, amendedQuantity, actor, Instant.now()));

        List<Trade> trades = generatedTrades.get();
        recordTrades(actor, trades);
        logger.info("Order {} replaced and generated {} trades", orderId, trades.size());

        return Optional.of(new OrderExecutionResult(order, trades));
    }

    /** O trade e causado pela ordem que chegou: o ator que a enviou e o ator do fato. */
    private void recordTrades(Actor actor, List<Trade> trades) {
        for (Trade trade : trades) {
            eventStore.append(new TradeExecuted(trade.getId(), trade.getSymbol(),
                trade.getBuyOrderId(), trade.getSellOrderId(), trade.getPrice(), trade.getQuantity(),
                trade.getBuyAccountId(), trade.getSellAccountId(), actor, trade.getTimestamp()));
            positionService.processTrade(trade);
            metricsCollector.recordTrade(trade);
        }
    }

    /**
     * Cancela a ordem em repouso. O journal so recebe o fato depois de o engine confirmar: um
     * 404 nao pode deixar OrderCancelled na trilha atribuido a quem chamou.
     *
     * Cancelar de novo e reenvio, nao erro: o estado desejado ja esta no lugar, e fato novo nao
     * ha. Ordem preenchida nao tem cancelamento possivel.
     */
    public CancelOutcome cancelOrder(Actor actor, String orderId, String symbol) {
        Order order = orderService.getOrder(orderId);
        if (order == null || !order.getSymbol().equals(symbol)) {
            return CancelOutcome.UNKNOWN;
        }

        if (matchingEngine.cancelOrder(orderId, symbol)) {
            eventStore.append(new OrderCancelled(orderId, symbol, actor, Instant.now()));
            logger.info("Order {} cancelled", orderId);
            return CancelOutcome.CANCELLED;
        }

        // O engine recusou: a ordem nao estava em repouso. Se ja estava cancelada, o que o
        // cliente pediu ja vale; se foi preenchida, nao ha o que cancelar.
        return order.getStatus() == OrderStatus.CANCELLED
            ? CancelOutcome.ALREADY_CANCELLED
            : CancelOutcome.NOT_CANCELLABLE;
    }

    /** O que o cancelamento fez. Reenvio nao gera fato: o journal ja tinha o do primeiro. */
    public enum CancelOutcome {
        CANCELLED,
        ALREADY_CANCELLED,
        NOT_CANCELLABLE,
        UNKNOWN
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
                    e.accountId(), e.timeInForce(), e.idempotencyKey()));
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
