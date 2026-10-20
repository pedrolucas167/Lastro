package io.github.pedrolucas167.lastro.ledger;

/** Por que o dinheiro se moveu: o que o extrato mostra em cada partida. */
public enum LedgerReason {
    OPENING,
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER,
    TRADE
}
