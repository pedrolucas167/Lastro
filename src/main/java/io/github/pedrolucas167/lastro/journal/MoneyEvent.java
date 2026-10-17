package io.github.pedrolucas167.lastro.journal;

/** Eventos de dinheiro: contas e transferencias. */
public sealed interface MoneyEvent extends DomainEvent
        permits AccountOpened, FundsCredited, FundsDebited, TransferCompleted {
}
