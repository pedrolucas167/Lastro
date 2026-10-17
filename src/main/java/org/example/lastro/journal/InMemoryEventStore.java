package org.example.lastro.journal;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Journal em memoria: usado em dev/demo/testes, onde a durabilidade entre reinicios nao e necessaria. */
public class InMemoryEventStore implements EventStore {
    private final List<DomainEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void append(DomainEvent event) {
        events.add(event);
    }

    @Override
    public List<DomainEvent> readAll() {
        return List.copyOf(events);
    }
}
