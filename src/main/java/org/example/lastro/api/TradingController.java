package org.example.lastro.api;

import jakarta.validation.Valid;
import org.example.lastro.domain.*;
import org.example.lastro.dto.*;
import org.example.lastro.service.OrderExecutionResult;
import org.example.lastro.service.TradingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/trading")
public class TradingController {
    private final TradingService tradingService;

    public TradingController(TradingService tradingService) {
        this.tradingService = tradingService;
    }

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        OrderSide side = OrderSide.valueOf(request.getSide().toUpperCase());
        OrderType type = OrderType.valueOf(request.getType().toUpperCase());
        TimeInForce timeInForce = resolveTimeInForce(request.getTimeInForce());
        
        OrderExecutionResult result = tradingService.submitOrder(
            request.getSymbol(),
            side,
            type,
            request.getPrice(),
            request.getQuantity(),
            request.getAccountId(),
            timeInForce
        );

        Order order = result.order();

        return ResponseEntity.ok(toResponse(order));
    }

    @PostMapping("/orders/{orderId}/replace")
    public ResponseEntity<OrderResponse> replaceOrder(@PathVariable String orderId,
                                                     @RequestParam String symbol,
                                                     @Valid @RequestBody ReplaceOrderRequest request) {
        return tradingService.replaceOrder(orderId, symbol, request.getPrice(), request.getQuantity())
            .map(result -> ResponseEntity.ok(toResponse(result.order())))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private OrderResponse toResponse(Order order) {
        return new OrderResponse(
            order.getId(),
            order.getSymbol(),
            order.getSide().name(),
            order.getType().name(),
            order.getTimeInForce().name(),
            order.getPrice(),
            order.getOriginalQuantity().intValue(),
            order.getRemainingQuantity(),
            order.getStatus().name(),
            order.getCreatedAt()
        );
    }

    private TimeInForce resolveTimeInForce(String value) {
        if (value == null || value.isBlank()) {
            return TimeInForce.GTC;
        }
        return TimeInForce.valueOf(value.toUpperCase());
    }

    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<Void> cancelOrder(@PathVariable String orderId, 
                                          @RequestParam String symbol) {
        boolean cancelled = tradingService.cancelOrder(orderId, symbol);
        return cancelled ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/orderbook/{symbol}")
    public ResponseEntity<OrderBookResponse> getOrderBook(@PathVariable String symbol) {
        OrderBook orderBook = tradingService.getOrderBook(symbol);
        
        return ResponseEntity.ok(new OrderBookResponse(
            symbol,
            orderBook.getBestBid().map(Order::getPrice).orElse(null),
            orderBook.getBestAsk().map(Order::getPrice).orElse(null),
            orderBook.getSpread(),
            orderBook.getTotalBidQuantity(),
            orderBook.getTotalAskQuantity()
        ));
    }

    @GetMapping("/trades")
    public ResponseEntity<List<TradeResponse>> getTrades(@RequestParam(required = false) String symbol) {
        List<Trade> trades = symbol != null 
            ? tradingService.getTradesForSymbol(symbol)
            : tradingService.getTrades();
        
        List<TradeResponse> responses = trades.stream()
            .map(t -> new TradeResponse(
                t.getId(),
                t.getSymbol(),
                t.getBuyOrderId(),
                t.getSellOrderId(),
                t.getPrice(),
                t.getQuantity(),
                t.getTimestamp()
            ))
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/positions/{accountId}/{symbol}")
    public ResponseEntity<PositionResponse> getPosition(@PathVariable String accountId,
                                                      @PathVariable String symbol,@RequestParam(required = false) BigDecimal currentPrice) {
        int quantity = tradingService.getPositionQuantity(accountId, symbol);
        BigDecimal avgPrice = tradingService.getPositionAveragePrice(accountId, symbol);
        
        return ResponseEntity.ok(new PositionResponse(
            accountId,
            symbol,
            quantity,
            avgPrice
        ));
    }

    @GetMapping("/positions/{accountId}/{symbol}/pnl")
    public ResponseEntity<BigDecimal> getPnL(@PathVariable String accountId,
                                            @PathVariable String symbol,
                                            @RequestParam BigDecimal currentPrice) {
        BigDecimal pnl = tradingService.getPositionPnL(accountId, symbol, currentPrice);
        return ResponseEntity.ok(pnl);
    }
}
