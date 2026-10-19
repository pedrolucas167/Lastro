package io.github.pedrolucas167.lastro.journal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/** No startup, reconstroi o estado em memoria a partir do journal. */
@Component
public class JournalReplay implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(JournalReplay.class);

    private final EventStore eventStore;
    private final List<JournalRebuildable> rebuildables;

    public JournalReplay(EventStore eventStore, List<JournalRebuildable> rebuildables) {
        this.eventStore = eventStore;
        this.rebuildables = rebuildables;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<DomainEvent> events = eventStore.readAll();
        if (events.isEmpty()) {
            return;
        }

        // A ordem respeita o @Order de cada rebuildable: o TradingService reconstroi o bloqueio das
        // ordens em aberto sobre as contas que o AccountService acabou de reconstruir.
        rebuildables.forEach(rebuildable -> rebuildable.rebuild(events));
        logger.info("Rebuilt in-memory state from {} journaled events", events.size());
    }
}
