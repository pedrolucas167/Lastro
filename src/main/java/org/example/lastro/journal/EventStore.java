package org.example.lastro.journal;

import java.util.List;

/** Armazenamento append-only de {@link DomainEvent}, usado para durabilidade e replay. */
public interface EventStore {

    void append(DomainEvent event);

    List<DomainEvent> readAll();
}
