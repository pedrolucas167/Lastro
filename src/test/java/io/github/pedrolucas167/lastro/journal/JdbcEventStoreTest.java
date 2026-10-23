package io.github.pedrolucas167.lastro.journal;

import io.github.pedrolucas167.lastro.domain.Actor;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Testcontainers(disabledWithoutDocker = true)
class JdbcEventStoreTest {
    private static final Actor OPERATOR = new Actor("operador", Set.of(Actor.OPERATOR));

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private JdbcTemplate jdbcTemplate;
    private JdbcEventStore eventStore;

    @BeforeEach
    void setUp() {
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .load()
            .migrate();

        jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbcTemplate.update("DELETE FROM journal_events");

        eventStore = new JdbcEventStore(jdbcTemplate, JsonMapper.builder().build());
    }

    @Test
    void roundTripsEventsInOrder() {
        Instant openedAt = Instant.parse("2026-01-01T10:00:00Z");
        eventStore.append(new AccountOpened("acc-1", new BigDecimal("1000.00"), OPERATOR, openedAt));
        eventStore.append(new FundsCredited("acc-1", new BigDecimal("250.00"), OPERATOR, openedAt.plusSeconds(60)));
        eventStore.append(new FundsDebited("acc-1", new BigDecimal("50.00"), OPERATOR, openedAt.plusSeconds(120)));

        List<DomainEvent> events = eventStore.readAll();

        assertEquals(3, events.size());
        assertEquals(new AccountOpened("acc-1", new BigDecimal("1000.00"), OPERATOR, openedAt), events.get(0));
        assertEquals(new FundsCredited("acc-1", new BigDecimal("250.00"), OPERATOR, openedAt.plusSeconds(60)), events.get(1));
        assertEquals(new FundsDebited("acc-1", new BigDecimal("50.00"), OPERATOR, openedAt.plusSeconds(120)), events.get(2));
    }

    @Test
    void roundTripsAccountHolderAssigned() {
        Instant openedAt = Instant.parse("2026-01-01T10:00:00Z");
        eventStore.append(new AccountOpened("acc-1", new BigDecimal("1000.00"), OPERATOR, openedAt));
        eventStore.append(new AccountHolderAssigned("acc-1", "cliente-1", OPERATOR, openedAt.plusSeconds(1)));

        List<DomainEvent> events = eventStore.readAll();

        assertEquals(2, events.size());
        assertEquals(new AccountHolderAssigned("acc-1", "cliente-1", OPERATOR, openedAt.plusSeconds(1)), events.get(1));
    }

    @Test
    void roundTripsMarketHaltEvents() {
        Instant haltedAt = Instant.parse("2026-01-01T10:00:00Z");
        eventStore.append(new SymbolHalted("PETR4", "circuito de protecao", OPERATOR, haltedAt));
        eventStore.append(new SymbolResumed("PETR4", OPERATOR, haltedAt.plusSeconds(600)));

        List<DomainEvent> events = eventStore.readAll();

        assertEquals(2, events.size());
        assertEquals(new SymbolHalted("PETR4", "circuito de protecao", OPERATOR, haltedAt), events.get(0));
        assertEquals(new SymbolResumed("PETR4", OPERATOR, haltedAt.plusSeconds(600)), events.get(1));
    }

    @Test
    void roundTripsClientEvents() {
        Instant at = Instant.parse("2026-01-01T10:00:00Z");
        eventStore.append(new ClientRegistered("cli-1", "titular-1", "Maria", OPERATOR, at));
        eventStore.append(new ClientSuspended("cli-1", "documentacao vencida", OPERATOR,
            at.plusSeconds(60)));
        eventStore.append(new ClientReactivated("cli-1", OPERATOR, at.plusSeconds(120)));

        List<DomainEvent> events = eventStore.readAll();

        assertEquals(3, events.size());
        assertEquals(new ClientRegistered("cli-1", "titular-1", "Maria", OPERATOR, at), events.get(0));
        assertEquals(new ClientSuspended("cli-1", "documentacao vencida", OPERATOR,
            at.plusSeconds(60)), events.get(1));
        assertEquals(new ClientReactivated("cli-1", OPERATOR, at.plusSeconds(120)), events.get(2));
    }

    @Test
    void roundTripsTransferCompletedWithTheIdempotencyKey() {
        Instant at = Instant.parse("2026-01-01T10:00:00Z");
        eventStore.append(new TransferCompleted("tx-1", "acc-1", "acc-2", new BigDecimal("50.00"), "chave-1",
            OPERATOR, at));

        List<DomainEvent> events = eventStore.readAll();

        assertEquals(1, events.size());
        assertEquals(new TransferCompleted("tx-1", "acc-1", "acc-2", new BigDecimal("50.00"), "chave-1",
            OPERATOR, at), events.get(0));
    }

    /** Transferencia gravada antes da chave nao tem o campo: o replay tem que continuar lendo. */
    @Test
    void replaysTransfersJournaledBeforeTheKeyExisted() {
        Instant at = Instant.parse("2026-01-01T10:00:00Z");
        jdbcTemplate.update(
            "INSERT INTO journal_events (event_type, payload, occurred_at) VALUES (?, ?, ?)",
            "TransferCompleted",
            "{\"transactionId\":\"tx-old\",\"fromAccountId\":\"acc-1\",\"toAccountId\":\"acc-2\","
                + "\"amount\":50.00,\"actor\":{\"subject\":\"operador\",\"roles\":[\"lastro-operator\"]},"
                + "\"occurredAt\":\"2026-01-01T10:00:00Z\"}",
            Timestamp.from(at));

        List<DomainEvent> events = eventStore.readAll();

        assertEquals(1, events.size());
        TransferCompleted event = (TransferCompleted) events.get(0);
        assertEquals("tx-old", event.transactionId());
        assertNull(event.idempotencyKey());
    }

    /** O journal gravado antes do ator nao tem o campo: o replay tem que continuar lendo. */
    @Test
    void replaysEventsJournaledBeforeTheActorExisted() {
        Instant openedAt = Instant.parse("2026-01-01T10:00:00Z");
        jdbcTemplate.update(
            "INSERT INTO journal_events (event_type, payload, occurred_at) VALUES (?, ?, ?)",
            "AccountOpened",
            "{\"accountId\":\"acc-old\",\"initialBalance\":1000.00,\"occurredAt\":\"2026-01-01T10:00:00Z\"}",
            Timestamp.from(openedAt));

        List<DomainEvent> events = eventStore.readAll();

        assertEquals(1, events.size());
        assertEquals(new AccountOpened("acc-old", new BigDecimal("1000.00"), null, openedAt), events.get(0));
        assertNull(events.get(0).actor());
    }
}
