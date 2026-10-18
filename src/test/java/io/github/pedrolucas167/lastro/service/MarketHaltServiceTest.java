package io.github.pedrolucas167.lastro.service;

import io.github.pedrolucas167.lastro.journal.DomainEvent;
import io.github.pedrolucas167.lastro.journal.EventStore;
import io.github.pedrolucas167.lastro.journal.InMemoryEventStore;
import io.github.pedrolucas167.lastro.journal.JournalReplay;
import io.github.pedrolucas167.lastro.journal.SymbolHalted;
import io.github.pedrolucas167.lastro.journal.SymbolResumed;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MarketHaltServiceTest {

    @Test
    @DisplayName("Aciona o halt de um símbolo com o motivo")
    void haltsSymbolWithReason() {
        MarketHaltService market = new MarketHaltService(new InMemoryEventStore());

        market.halt("PETR4", "circuito de protecao");

        assertTrue(market.isHalted("PETR4"));
        assertEquals("circuito de protecao", market.haltReason("PETR4"));
        assertEquals(Map.of("PETR4", "circuito de protecao"), market.haltedSymbols());
    }

    @Test
    @DisplayName("Halt sem motivo informado registra um motivo padrao")
    void haltWithoutReasonRecordsADefaultReason() {
        MarketHaltService market = new MarketHaltService(new InMemoryEventStore());

        market.halt("PETR4", "   ");

        assertTrue(market.isHalted("PETR4"));
        assertFalse(market.haltReason("PETR4").isBlank());
    }

    @Test
    @DisplayName("O halt vale apenas para o símbolo acionado")
    void haltIsScopedToTheSymbol() {
        MarketHaltService market = new MarketHaltService(new InMemoryEventStore());

        market.halt("PETR4", "circuito");

        assertTrue(market.isHalted("PETR4"));
        assertFalse(market.isHalted("VALE3"));
    }

    @Test
    @DisplayName("O resume libera o símbolo")
    void resumeReleasesTheSymbol() {
        MarketHaltService market = new MarketHaltService(new InMemoryEventStore());
        market.halt("PETR4", "circuito");

        market.resume("PETR4");

        assertFalse(market.isHalted("PETR4"));
        assertNull(market.haltReason("PETR4"));
        assertTrue(market.haltedSymbols().isEmpty());
    }

    @Test
    @DisplayName("Halt e resume são fatos do journal, com o motivo")
    void recordsHaltAndResumeInTheJournal() {
        EventStore eventStore = new InMemoryEventStore();
        MarketHaltService market = new MarketHaltService(eventStore);

        market.halt("PETR4", "circuito de protecao");
        market.resume("VALE3");

        List<DomainEvent> events = eventStore.readAll();
        assertEquals(2, events.size());

        SymbolHalted halted = assertInstanceOf(SymbolHalted.class, events.get(0));
        assertEquals("PETR4", halted.symbol());
        assertEquals("circuito de protecao", halted.reason());
        assertNotNull(halted.occurredAt());

        SymbolResumed resumed = assertInstanceOf(SymbolResumed.class, events.get(1));
        assertEquals("VALE3", resumed.symbol());
        assertNotNull(resumed.occurredAt());
    }

    @Test
    @DisplayName("Um símbolo pausado volta pausado depois de um restart")
    void haltedSymbolSurvivesRestart() {
        EventStore eventStore = new InMemoryEventStore();
        new MarketHaltService(eventStore).halt("PETR4", "circuito de protecao");

        // Simula um reinicio: servico novo, mesmo journal.
        MarketHaltService replayed = new MarketHaltService(eventStore);
        replayed.rebuild(eventStore.readAll());

        assertTrue(replayed.isHalted("PETR4"));
        assertEquals("circuito de protecao", replayed.haltReason("PETR4"));
    }

    @Test
    @DisplayName("Um símbolo liberado continua liberado depois de um restart")
    void resumedSymbolStaysReleasedAfterRestart() {
        EventStore eventStore = new InMemoryEventStore();
        MarketHaltService before = new MarketHaltService(eventStore);
        before.halt("PETR4", "circuito");
        before.resume("PETR4");

        MarketHaltService replayed = new MarketHaltService(eventStore);
        replayed.rebuild(eventStore.readAll());

        assertFalse(replayed.isHalted("PETR4"));
    }

    @Test
    @DisplayName("O JournalReplay reconstrói o halt no startup")
    void journalReplayRebuildsHaltState() {
        EventStore eventStore = new InMemoryEventStore();
        new MarketHaltService(eventStore).halt("PETR4", "circuito");

        MarketHaltService reader = new MarketHaltService(eventStore);
        new JournalReplay(eventStore, List.of(reader)).run(null);

        assertTrue(reader.isHalted("PETR4"));
        assertEquals("circuito", reader.haltReason("PETR4"));
    }
}
