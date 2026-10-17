package io.github.pedrolucas167.lastro.service;

import io.github.pedrolucas167.lastro.domain.Position;
import io.github.pedrolucas167.lastro.domain.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PositionService {
    private static final Logger logger = LoggerFactory.getLogger(PositionService.class);
    private final Map<String, Position> positions = new ConcurrentHashMap<>();

    public Position getPosition(String accountId, String symbol) {
        String key = accountId + ":" + symbol;
        return positions.computeIfAbsent(key, k -> new Position(accountId, symbol));
    }

    public void processTrade(Trade trade) {
        String buyAccountId = trade.getBuyAccountId();
        String sellAccountId = trade.getSellAccountId();
        Position buyPosition = getPosition(buyAccountId, trade.getSymbol());
        Position sellPosition = getPosition(sellAccountId, trade.getSymbol());

        buyPosition.addBuy(trade.getQuantity(), trade.getPrice());
        sellPosition.addSell(trade.getQuantity(), trade.getPrice());

        logger.info("Updated positions for trade {}: Buy {} {} @ {}, Sell {} {} @ {}",
            trade.getId(), buyAccountId, trade.getQuantity(), trade.getPrice(),
            sellAccountId, trade.getQuantity(), trade.getPrice());
    }

    public int getQuantity(String accountId, String symbol) {
        Position position = getPosition(accountId, symbol);
        return position.getQuantity();
    }

    public BigDecimal getAveragePrice(String accountId, String symbol) {
        Position position = getPosition(accountId, symbol);
        return position.getAveragePrice();
    }

    public BigDecimal getMarketValue(String accountId, String symbol, BigDecimal currentPrice) {
        Position position = getPosition(accountId, symbol);
        return currentPrice.multiply(new BigDecimal(position.getQuantity()));
    }

    public BigDecimal getPnL(String accountId, String symbol, BigDecimal currentPrice) {
        Position position = getPosition(accountId, symbol);
        if (position.getQuantity() == 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal marketValue = currentPrice.multiply(new BigDecimal(position.getQuantity()));
        BigDecimal costBasis = position.getAveragePrice().multiply(new BigDecimal(position.getQuantity()));
        return marketValue.subtract(costBasis);
    }

    public void clear() {
        positions.clear();
    }
}
