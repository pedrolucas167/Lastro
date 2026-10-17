package org.example.lastro.journal;

import org.example.lastro.service.AccountService;
import org.example.lastro.service.TransactionService;
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
    private final AccountService accountService;
    private final TransactionService transactionService;

    public JournalReplay(EventStore eventStore, AccountService accountService,
                         TransactionService transactionService) {
        this.eventStore = eventStore;
        this.accountService = accountService;
        this.transactionService = transactionService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<DomainEvent> events = eventStore.readAll();
        if (events.isEmpty()) {
            return;
        }

        accountService.rebuild(events);
        transactionService.rebuild(events);
        logger.info("Rebuilt in-memory state from {} journaled events", events.size());
    }
}
