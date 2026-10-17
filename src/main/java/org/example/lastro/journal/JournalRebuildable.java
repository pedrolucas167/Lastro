package org.example.lastro.journal;

import java.util.List;

/**
 * Servico cujo estado em memoria e reconstruivel a partir do journal.
 * Cada implementacao aplica apenas os eventos que lhe dizem respeito.
 */
public interface JournalRebuildable {

    /** Reconstroi o estado a partir do journal, sem regravar eventos. */
    void rebuild(List<DomainEvent> events);
}
