package org.example.lastro.journal;

import java.time.Instant;

/** Fato de dominio imutavel registrado no journal append-only. */
public sealed interface DomainEvent
        permits AccountOpened, FundsCredited, FundsDebited, TransferCompleted {

    String type();

    Instant occurredAt();
}
