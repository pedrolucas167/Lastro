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

    /** Bloqueia acoes para uma ordem de venda; false quando a posicao livre nao cobre. */
    public boolean hold(String accountId, String symbol, int quantity) {
        return getPosition(accountId, symbol).hold(quantity);
    }

    /** Devolve as acoes bloqueadas que a ordem nao vai mais vender. */
    public void release(String accountId, String symbol, int quantity) {
        getPosition(accountId, symbol).release(quantity);
    }

    /** Acoes livres para vender: a posicao menos o que esta em ordens de venda. */
    public int getAvailable(String accountId, String symbol) {
        return getPosition(accountId, symbol).getAvailable();
    }

    /** Restaura o bloqueado no replay, a partir da soma das ordens de venda em aberto. */
    public void rebuildHeld(String accountId, String symbol, int quantity) {
        getPosition(accountId, symbol).rebuildHeld(quantity);
    }

    /**
     * Custodia: a acao entra numa conta, vinda de outra conta ou de fora do sistema (origem nula),
     * no custo medio informado. Vale no comando e no replay, que e o mesmo movimento sem fato novo.
     */
    public void applyCustody(String fromAccountId, String toAccountId, String symbol, int quantity,
                             BigDecimal price) {
        if (fromAccountId != null) {
            getPosition(fromAccountId, symbol).addSell(quantity, price);
        }
        getPosition(toAccountId, symbol).addBuy(quantity, price);
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
