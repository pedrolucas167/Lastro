package io.github.pedrolucas167.lastro.journal;

/** Eventos de mercado: pausa e retomada de um simbolo. */
public sealed interface MarketEvent extends DomainEvent
        permits SymbolHalted, SymbolResumed {
}
