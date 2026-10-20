package io.github.pedrolucas167.lastro.ledger;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Uma linha do extrato de um cliente: a partida que tocou o caixa dele, de onde veio (ou para onde
 * foi) e o saldo depois dela. A contraparte de um movimento entre clientes vem sem o id -- o extrato
 * de um nao revela a conta do outro.
 */
public record LedgerLine(String reference, LedgerReason reason, Instant occurredAt, LedgerSide side,
                         BigDecimal amount, String counterparty, BigDecimal balanceAfter) {
}
