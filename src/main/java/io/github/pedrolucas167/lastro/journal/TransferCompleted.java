package io.github.pedrolucas167.lastro.journal;

import io.github.pedrolucas167.lastro.domain.Actor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Fato: uma transferencia entre contas aconteceu.
 *
 * A chave de idempotencia do cliente entra no fato: e ela que diz, no replay, que um reenvio
 * com a mesma chave nao pode virar uma segunda transferencia. Nula quando o cliente nao mandou
 * chave. Transferencia que falhou nao vira evento, entao nao ha chave presa a ela.
 */
public record TransferCompleted(String transactionId, String fromAccountId, String toAccountId,
                                BigDecimal amount, String idempotencyKey, Actor actor, Instant occurredAt)
        implements MoneyEvent {

    @Override
    public String type() {
        return "TransferCompleted";
    }
}
