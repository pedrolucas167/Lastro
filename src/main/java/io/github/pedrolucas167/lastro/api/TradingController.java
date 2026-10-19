package io.github.pedrolucas167.lastro.api;

import jakarta.validation.Valid;
import io.github.pedrolucas167.lastro.config.Roles;
import io.github.pedrolucas167.lastro.domain.*;
import io.github.pedrolucas167.lastro.dto.*;
import io.github.pedrolucas167.lastro.service.CustodyService;
import io.github.pedrolucas167.lastro.service.CustodyTransfer;
import io.github.pedrolucas167.lastro.service.OrderExecutionResult;
import io.github.pedrolucas167.lastro.service.TradingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/trading")
public class TradingController {
    private final TradingService tradingService;
    private final CustodyService custodyService;

    public TradingController(TradingService tradingService, CustodyService custodyService) {
        this.tradingService = tradingService;
        this.custodyService = custodyService;
    }

    /**
     * `Idempotency-Key` e a chave do cliente para reenvio: repetir a chamada com a mesma chave
     * devolve a ordem que ja foi aceita, sem criar outra.
     */
    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> createOrder(Authentication authentication,
                                                     @RequestHeader(value = "Idempotency-Key", required = false)
                                                     String idempotencyKey,
                                                     @Valid @RequestBody CreateOrderRequest request) {
        OrderSide side = OrderSide.valueOf(request.getSide().toUpperCase());
        OrderType type = OrderType.valueOf(request.getType().toUpperCase());
        TimeInForce timeInForce = resolveTimeInForce(request.getTimeInForce());
        
        OrderExecutionResult result = tradingService.submitOrder(
            Roles.actor(authentication),
            request.getSymbol(),
            side,
            type,
            request.getPrice(),
            request.getQuantity(),
            request.getAccountId(),
            timeInForce,
            idempotencyKey
        );

        Order order = result.order();

        return ResponseEntity.ok(toResponse(order));
    }

    @PostMapping("/orders/{orderId}/replace")
    public ResponseEntity<OrderResponse> replaceOrder(Authentication authentication,
                                                     @PathVariable String orderId,
                                                     @RequestParam String symbol,
                                                     @Valid @RequestBody ReplaceOrderRequest request) {
        return tradingService.replaceOrder(Roles.actor(authentication), orderId, symbol,
                request.getPrice(), request.getQuantity())
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

    /**
     * Cancelar de novo e reenvio: responde 200 sem fato novo, porque o journal ja tem o
     * cancelamento do primeiro. Ordem preenchida nao aceita cancelamento (409) e ordem
     * desconhecida e 404.
     */
    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<Void> cancelOrder(Authentication authentication,
                                          @PathVariable String orderId, 
                                          @RequestParam String symbol) {
        return switch (tradingService.cancelOrder(Roles.actor(authentication), orderId, symbol)) {
            case CANCELLED, ALREADY_CANCELLED -> ResponseEntity.ok().build();
            case NOT_CANCELLABLE -> ResponseEntity.status(HttpStatus.CONFLICT).build();
            case UNKNOWN -> ResponseEntity.notFound().build();
        };
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

    /**
     * Deposito de custodia: a acao entra no sistema, vinda de fora, no custo medio informado. E
     * como a acao passa a existir para ser vendida.
     */
    @PostMapping("/positions/deposit")
    public ResponseEntity<CustodyTransferResponse> depositCustody(
            Authentication authentication, @Valid @RequestBody CustodyDepositRequest request) {
        CustodyTransfer transfer = custodyService.deposit(Roles.actor(authentication),
            request.getAccountId(), request.getSymbol(), request.getQuantity(),
            request.getPrice());
        return ResponseEntity.ok(CustodyTransferResponse.of(transfer));
    }

    /** Custodia: a acao passa de uma conta para outra sem passar pelo mercado. */
    @PostMapping("/positions/transfer")
    public ResponseEntity<CustodyTransferResponse> transferCustody(
            Authentication authentication, @Valid @RequestBody CustodyTransferRequest request) {
        CustodyTransfer transfer = custodyService.transfer(Roles.actor(authentication),
            request.getFromAccountId(), request.getToAccountId(), request.getSymbol(),
            request.getQuantity());
        return ResponseEntity.ok(CustodyTransferResponse.of(transfer));
    }
}
