package org.example.lastro.journal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/** Journal durável em banco relacional (Postgres em producao, H2 embutido em dev/testes). */
@Repository
public class JdbcEventStore implements EventStore {
    private static final Map<String, Class<? extends DomainEvent>> TYPES = Map.of(
        "AccountOpened", AccountOpened.class,
        "FundsCredited", FundsCredited.class,
        "FundsDebited", FundsDebited.class,
        "TransferCompleted", TransferCompleted.class,
        "OrderSubmitted", OrderSubmitted.class,
        "OrderCancelled", OrderCancelled.class,
        "OrderReplaced", OrderReplaced.class,
        "TradeExecuted", TradeExecuted.class
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcEventStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(DomainEvent event) {
        jdbcTemplate.update(
            "INSERT INTO journal_events (event_type, payload, occurred_at) VALUES (?, ?, ?)",
            event.type(),
            objectMapper.writeValueAsString(event),
            Timestamp.from(event.occurredAt()));
    }

    @Override
    public List<DomainEvent> readAll() {
        return jdbcTemplate.query(
            "SELECT event_type, payload FROM journal_events ORDER BY sequence_number",
            (rs, rowNum) -> deserialize(rs.getString("event_type"), rs.getString("payload")));
    }

    private DomainEvent deserialize(String type, String payload) {
        Class<? extends DomainEvent> eventClass = TYPES.get(type);
        if (eventClass == null) {
            throw new IllegalStateException("Unknown journal event type: " + type);
        }
        return objectMapper.readValue(payload, eventClass);
    }
}
