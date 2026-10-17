package org.example.lastro.journal;

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
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
class JdbcEventStoreTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private JdbcEventStore eventStore;

    @BeforeEach
    void setUp() {
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .load()
            .migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbcTemplate.update("DELETE FROM journal_events");

        eventStore = new JdbcEventStore(jdbcTemplate, JsonMapper.builder().build());
    }

    @Test
    void roundTripsEventsInOrder() {
        Instant openedAt = Instant.parse("2026-01-01T10:00:00Z");
        eventStore.append(new AccountOpened("acc-1", new BigDecimal("1000.00"), openedAt));
        eventStore.append(new FundsCredited("acc-1", new BigDecimal("250.00"), openedAt.plusSeconds(60)));
        eventStore.append(new FundsDebited("acc-1", new BigDecimal("50.00"), openedAt.plusSeconds(120)));

        List<DomainEvent> events = eventStore.readAll();

        assertEquals(3, events.size());
        assertEquals(new AccountOpened("acc-1", new BigDecimal("1000.00"), openedAt), events.get(0));
        assertEquals(new FundsCredited("acc-1", new BigDecimal("250.00"), openedAt.plusSeconds(60)), events.get(1));
        assertEquals(new FundsDebited("acc-1", new BigDecimal("50.00"), openedAt.plusSeconds(120)), events.get(2));
    }
}
