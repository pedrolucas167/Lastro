package io.github.pedrolucas167.lastro.service;

import io.github.pedrolucas167.lastro.domain.Actor;
import io.github.pedrolucas167.lastro.journal.DomainEvent;
import io.github.pedrolucas167.lastro.journal.EventStore;
import io.github.pedrolucas167.lastro.journal.JournalRebuildable;
import io.github.pedrolucas167.lastro.journal.MarketEvent;
import io.github.pedrolucas167.lastro.journal.SymbolHalted;
import io.github.pedrolucas167.lastro.journal.SymbolResumed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Circuit breaker por simbolo: acionado, rejeita novas ordens do simbolo ate o resume.
 * As ordens ja no book permanecem -- o trading fica pausado, nao cancelado -- e continuam
 * podendo ser canceladas.
 *
 * O halt e um fato do journal, nao estado volatil: um restart tem que religar o trading
 * so quando alguem mandar, nunca sozinho.
 */
@Service
public class MarketHaltService implements JournalRebuildable {
    private static final Logger logger = LoggerFactory.getLogger(MarketHaltService.class);
    private static final String NO_REASON = "no reason given";

    private final Map<String, String> haltReasons = new ConcurrentHashMap<>();
    private final EventStore eventStore;

    public MarketHaltService(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    public void halt(Actor actor, String symbol, String reason) {
        String recordedReason = reason == null || reason.isBlank() ? NO_REASON : reason;
        eventStore.append(new SymbolHalted(symbol, recordedReason, actor, Instant.now()));
        haltReasons.put(symbol, recordedReason);
        logger.info("Trading halted for {}: {}", symbol, recordedReason);
    }

    public void resume(Actor actor, String symbol) {
        eventStore.append(new SymbolResumed(symbol, actor, Instant.now()));
        haltReasons.remove(symbol);
        logger.info("Trading resumed for {}", symbol);
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

    /** Reconstroi o estado a partir do journal, sem regravar eventos. */
    public void rebuild(List<DomainEvent> events) {
        haltReasons.clear();
        for (DomainEvent event : events) {
            if (event instanceof MarketEvent marketEvent) {
                apply(marketEvent);
            }
        }
    }

    private void apply(MarketEvent event) {
        switch (event) {
            case SymbolHalted e -> haltReasons.put(e.symbol(), e.reason());
            case SymbolResumed e -> haltReasons.remove(e.symbol());
        }
    }
}
