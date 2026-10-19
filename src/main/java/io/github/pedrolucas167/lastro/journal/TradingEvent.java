package io.github.pedrolucas167.lastro.journal;

/** Eventos do engine de negociacao: comandos de ordens, trades executados e custodia. */
public sealed interface TradingEvent extends DomainEvent
        permits OrderSubmitted, OrderCancelled, OrderReplaced, TradeExecuted, CustodyTransferred {
}
