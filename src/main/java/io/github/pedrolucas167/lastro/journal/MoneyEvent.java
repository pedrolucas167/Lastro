package io.github.pedrolucas167.lastro.journal;

/** Eventos de dinheiro: contas (incluindo titularidade) e transferencias. */
public sealed interface MoneyEvent extends DomainEvent
        permits AccountOpened, AccountHolderAssigned, FundsCredited, FundsDebited, TransferCompleted {
}
