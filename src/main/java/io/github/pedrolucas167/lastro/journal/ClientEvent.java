package io.github.pedrolucas167.lastro.journal;

/**
 * Eventos de cliente: quem ele e e se pode operar. Ficam no journal como os de dinheiro -- o
 * cadastro e o status tambem sao fatos, e o replay os reconstroi.
 */
public sealed interface ClientEvent extends DomainEvent
        permits ClientRegistered, ClientSuspended, ClientReactivated, ClientLimitsSet {
}
