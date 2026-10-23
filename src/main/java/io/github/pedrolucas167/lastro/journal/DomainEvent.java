package io.github.pedrolucas167.lastro.journal;

import io.github.pedrolucas167.lastro.domain.Actor;

import java.time.Instant;

/** Fato de dominio imutavel registrado no journal append-only. */
public sealed interface DomainEvent permits MoneyEvent, TradingEvent, MarketEvent, ClientEvent {

    String type();

    Instant occurredAt();

    /** Quem causou o fato, com os papeis que carregava. Nulo nos eventos gravados antes disso. */
    Actor actor();
}
