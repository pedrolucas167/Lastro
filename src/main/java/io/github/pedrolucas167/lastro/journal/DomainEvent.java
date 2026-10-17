package io.github.pedrolucas167.lastro.journal;

import java.time.Instant;

/** Fato de dominio imutavel registrado no journal append-only. */
public sealed interface DomainEvent permits MoneyEvent, TradingEvent {

    String type();

    Instant occurredAt();
}
