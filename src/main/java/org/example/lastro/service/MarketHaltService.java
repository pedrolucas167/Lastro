package org.example.lastro.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Circuit breaker por simbolo: quando acionado, novas ordens do simbolo sao rejeitadas
 * ate o resume. As ordens ja no book permanecem (o trading fica pausado, nao cancelado).
 */
@Service
public class MarketHaltService {
    private static final String NO_REASON = "no reason given";

    private final Map<String, String> haltReasons = new ConcurrentHashMap<>();

    public void halt(String symbol, String reason) {
        haltReasons.put(symbol, reason == null || reason.isBlank() ? NO_REASON : reason);
    }

    public void resume(String symbol) {
        haltReasons.remove(symbol);
    }

    public boolean isHalted(String symbol) {
        return haltReasons.containsKey(symbol);
    }

    public String haltReason(String symbol) {
        return haltReasons.get(symbol);
    }

    public Map<String, String> haltedSymbols() {
        return Map.copyOf(haltReasons);
    }
}
