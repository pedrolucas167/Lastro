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

@Service
public class RegulatoryValidationService {
    private static final Logger logger = LoggerFactory.getLogger(RegulatoryValidationService.class);
    
    private static final BigDecimal MIN_PRICE = new BigDecimal("0.01");
    private static final int MIN_QUANTITY = 1;
    private static final int MAX_QUANTITY = 1000000;
    private static final BigDecimal MAX_ORDER_VALUE = new BigDecimal("100000000");

    private final PriceBandProperties priceBand;
    private final boolean marketEnabled;
    private final boolean weekendTradingEnabled;
    private final LocalTime marketOpen;
    private final LocalTime marketClose;

    public RegulatoryValidationService(
            PriceBandProperties priceBand,
            @Value("${lastro.market.enabled:true}") boolean marketEnabled,
            @Value("${lastro.market.weekend-trading-enabled:false}") boolean weekendTradingEnabled,
            @Value("${lastro.market.open:10:00}") String marketOpen,
            @Value("${lastro.market.close:18:00}") String marketClose) {
        this.priceBand = priceBand;
        this.marketEnabled = marketEnabled;
        this.weekendTradingEnabled = weekendTradingEnabled;
        this.marketOpen = LocalTime.parse(marketOpen);
        this.marketClose = LocalTime.parse(marketClose);
    }

    public void validateOrder(String symbol, OrderSide side, OrderType type, 
                             BigDecimal price, int quantity, String accountId) {
        validateMarketHours();
        validateQuantity(quantity);
        validatePrice(price, type);
        validatePriceBand(symbol, price, type);
        validateOrderValue(price, quantity);
        validateSymbol(symbol);
        
        logger.info("Regulatory validation passed for order {} {} {} @ {} for account {}", 
            side, type, quantity, price, accountId);
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
        if (!priceBand.isEnabled() || type == OrderType.MARKET || price == null) {
            return;
        }

        BigDecimal reference = priceBand.getReferencePrices().get(symbol);
        if (reference == null) {
            return;
        }

        BigDecimal factor = priceBand.getPercent().movePointLeft(2);
        BigDecimal lowerBound = reference.multiply(BigDecimal.ONE.subtract(factor));
        BigDecimal upperBound = reference.multiply(BigDecimal.ONE.add(factor));

        if (price.compareTo(lowerBound) < 0 || price.compareTo(upperBound) > 0) {
            throw new RegulatoryException(String.format(
                "Price %s for %s is outside the allowed band [%s, %s]",
                price, symbol, lowerBound, upperBound));
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

    public void validateSufficientBalance(BigDecimal balance, BigDecimal requiredAmount) {
        if (balance.compareTo(requiredAmount) < 0) {
            throw new RegulatoryException(
                String.format("Insufficient balance: required %s, available %s", 
                    requiredAmount, balance)
            );
        }
    }

    public void validateSufficientPosition(int currentPosition, int requiredQuantity) {
        if (currentPosition < requiredQuantity) {
            throw new RegulatoryException(
                String.format("Insufficient position: required %d, available %d", 
                    requiredQuantity, currentPosition)
            );
        }
    }
}
