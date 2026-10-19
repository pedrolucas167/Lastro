package io.github.pedrolucas167.lastro.service;

import io.github.pedrolucas167.lastro.config.PriceBandProperties;
import io.github.pedrolucas167.lastro.domain.OrderSide;
import io.github.pedrolucas167.lastro.domain.OrderType;
import io.github.pedrolucas167.lastro.exception.RegulatoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class RegulatoryValidationService {
    private static final Logger logger = LoggerFactory.getLogger(RegulatoryValidationService.class);
    
    private static final BigDecimal MIN_PRICE = new BigDecimal("0.01");
    private static final int MIN_QUANTITY = 1;
    private static final int MAX_QUANTITY = 1000000;
    private static final BigDecimal MAX_ORDER_VALUE = new BigDecimal("100000000");

    private final MarketHaltService marketHaltService;
    private final PriceBandProperties priceBand;
    private final boolean marketEnabled;
    private final boolean weekendTradingEnabled;
    private final LocalTime marketOpen;
    private final LocalTime marketClose;

    public RegulatoryValidationService(
            MarketHaltService marketHaltService,
            PriceBandProperties priceBand,
            @Value("${lastro.market.enabled:true}") boolean marketEnabled,
            @Value("${lastro.market.weekend-trading-enabled:false}") boolean weekendTradingEnabled,
            @Value("${lastro.market.open:10:00}") String marketOpen,
            @Value("${lastro.market.close:18:00}") String marketClose) {
        this.marketHaltService = marketHaltService;
        this.priceBand = priceBand;
        this.marketEnabled = marketEnabled;
        this.weekendTradingEnabled = weekendTradingEnabled;
        this.marketOpen = LocalTime.parse(marketOpen);
        this.marketClose = LocalTime.parse(marketClose);
    }

    public void validateOrder(String symbol, OrderSide side, OrderType type, 
                             BigDecimal price, int quantity, String accountId) {
        validateNotHalted(symbol);
        validateMarketHours();
        validateQuantity(quantity);
        validatePrice(price, type);
        validatePriceBand(symbol, price, type);
        validateOrderValue(price, quantity);
        validateSymbol(symbol);
        
        logger.info("Regulatory validation passed for order {} {} {} @ {} for account {}", 
            side, type, quantity, price, accountId);
    }

    /** O formato do simbolo vale para qualquer comando sobre uma acao, nao so para ordem. */
    public void validateSymbolFormat(String symbol) {
        validateSymbol(symbol);
    }

    /**
     * Vale para ordem nova e para amend (os dois passam por aqui). Cancelar nao passa, e
     * e proposital: num simbolo pausado o operador precisa poder desmontar o book.
     */
    private void validateNotHalted(String symbol) {
        if (marketHaltService.isHalted(symbol)) {
            throw new RegulatoryException(String.format("Trading halted for %s: %s",
                symbol, marketHaltService.haltReason(symbol)));
        }
    }

    private void validateMarketHours() {
        if (!marketEnabled) {
            return;
        }

        LocalTime now = LocalTime.now();
        DayOfWeek dayOfWeek = java.time.DayOfWeek.from(java.time.LocalDate.now());
        
        if (!weekendTradingEnabled &&
            (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY)) {
            throw new RegulatoryException("Market is closed on weekends");
        }
        
        if (now.isBefore(marketOpen) || now.isAfter(marketClose)) {
            throw new RegulatoryException(
                String.format("Market is closed. Trading hours: %s - %s", 
                    marketOpen.truncatedTo(ChronoUnit.MINUTES), 
                    marketClose.truncatedTo(ChronoUnit.MINUTES))
            );
        }
    }

    private void validateQuantity(int quantity) {
        if (quantity < MIN_QUANTITY) {
            throw new RegulatoryException(
                String.format("Quantity %d is below minimum %d", quantity, MIN_QUANTITY)
            );
        }
        
        if (quantity > MAX_QUANTITY) {
            throw new RegulatoryException(
                String.format("Quantity %d exceeds maximum %d", quantity, MAX_QUANTITY)
            );
        }
    }

    private void validatePrice(BigDecimal price, OrderType type) {
        if (type == OrderType.LIMIT) {
            if (price == null) {
                throw new RegulatoryException("Price is required for limit orders");
            }
            
            if (price.compareTo(MIN_PRICE) < 0) {
                throw new RegulatoryException(
                    String.format("Price %s is below minimum %s", price, MIN_PRICE)
                );
            }
            
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RegulatoryException("Price must be positive");
            }
        }
    }

    private void validatePriceBand(String symbol, BigDecimal price, OrderType type) {
        if (type == OrderType.MARKET || price == null) {
            return;
        }

        Optional<BigDecimal> lowerBound = priceBand.lowerBound(symbol);
        Optional<BigDecimal> upperBound = priceBand.upperBound(symbol);
        if (lowerBound.isEmpty() || upperBound.isEmpty()) {
            // Simbolo sem referencia configurada nao tem banda.
            return;
        }

        if (price.compareTo(lowerBound.get()) < 0 || price.compareTo(upperBound.get()) > 0) {
            throw new RegulatoryException(String.format(
                "Price %s for %s is outside the allowed band [%s, %s]",
                price, symbol, lowerBound.get(), upperBound.get()));
        }
    }

    private void validateOrderValue(BigDecimal price, int quantity) {
        if (price != null) {
            BigDecimal orderValue = price.multiply(new BigDecimal(quantity));
            if (orderValue.compareTo(MAX_ORDER_VALUE) > 0) {
                throw new RegulatoryException(
                    String.format("Order value %s exceeds maximum %s", orderValue, MAX_ORDER_VALUE)
                );
            }
        }
    }

    private void validateSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new RegulatoryException("Symbol is required");
        }
        
        if (symbol.length() < 4 || symbol.length() > 10) {
            throw new RegulatoryException(
                String.format("Symbol %s must be between 4 and 10 characters", symbol)
            );
        }
        
        if (!symbol.matches("^[A-Z0-9]+$")) {
            throw new RegulatoryException(
                String.format("Symbol %s must contain only uppercase letters and numbers", symbol)
            );
        }
    }
}
