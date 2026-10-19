package io.github.pedrolucas167.lastro.journal;

import io.github.pedrolucas167.lastro.domain.Actor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Fato: acoes mudaram de conta. Sem conta de origem, e deposito -- a acao vem de fora do sistema.
 * O preco medio com que ela entrou fica no fato: e o custo que o destino passa a ter, e o replay
 * nao precisa deduzi-lo das posicoes.
 */
public record CustodyTransferred(String fromAccountId, String toAccountId, String symbol,
                                 int quantity, BigDecimal price, Actor actor, Instant occurredAt)
        implements TradingEvent {

    @Override
    public String type() {
        return "CustodyTransferred";
    }
}
