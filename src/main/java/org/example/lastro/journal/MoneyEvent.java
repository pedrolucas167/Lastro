package org.example.lastro.journal;

/** Eventos de dinheiro: contas e transferencias. */
public sealed interface MoneyEvent extends DomainEvent
        permits AccountOpened, FundsCredited, FundsDebited, TransferCompleted {
}
