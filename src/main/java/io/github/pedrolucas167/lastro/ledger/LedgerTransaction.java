package io.github.pedrolucas167.lastro.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Um fato economico em partidas dobradas: de onde o dinheiro saiu e para onde foi, no mesmo
 * instante. A soma dos debitos tem de ser igual a soma dos creditos -- dinheiro nao aparece nem
 * desaparece no caminho, e e essa igualdade que o construtor recusa quebrar.
 *
 * Quem descreve o fato e o journal (deposito, transferencia, trade); esta classe so o traduz para a
 * contabilidade. As fabricas abaixo sao o unico lugar que conhece o mapa de cada movimento.
 */
public record LedgerTransaction(String reference, LedgerReason reason, Instant occurredAt,
                                List<Posting> postings) {

    public LedgerTransaction {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(occurredAt, "occurredAt");
        postings = List.copyOf(postings);
        if (postings.size() < 2) {
            throw new IllegalArgumentException(
                "A ledger transaction needs at least two postings: " + postings.size());
        }
        BigDecimal debits = total(postings, LedgerSide.DEBIT);
        BigDecimal credits = total(postings, LedgerSide.CREDIT);
        if (debits.compareTo(credits) != 0) {
            throw new IllegalArgumentException(
                "Unbalanced ledger transaction: debits " + debits + ", credits " + credits);
        }
    }

    /** Dinheiro entra na corretora: o caixa na liquidante do um lado, o cliente do outro. */
    public static LedgerTransaction deposit(String reference, String accountId, BigDecimal amount,
                                            Instant occurredAt) {
        return of(reference, LedgerReason.DEPOSIT, occurredAt,
            new Posting(ChartOfAccounts.SETTLEMENT, LedgerSide.DEBIT, amount),
            new Posting(ChartOfAccounts.client(accountId), LedgerSide.CREDIT, amount));
    }

    /** Dinheiro sai da corretora pela mesma porta por onde entrou. */
    public static LedgerTransaction withdrawal(String reference, String accountId, BigDecimal amount,
                                               Instant occurredAt) {
        return of(reference, LedgerReason.WITHDRAWAL, occurredAt,
            new Posting(ChartOfAccounts.client(accountId), LedgerSide.DEBIT, amount),
            new Posting(ChartOfAccounts.SETTLEMENT, LedgerSide.CREDIT, amount));
    }

    /** De um cliente para outro: o caixa da corretora nao se move, so muda de dono. */
    public static LedgerTransaction transfer(String reference, String fromAccountId,
                                             String toAccountId, BigDecimal amount, Instant occurredAt) {
        return of(reference, LedgerReason.TRANSFER, occurredAt,
            new Posting(ChartOfAccounts.client(fromAccountId), LedgerSide.DEBIT, amount),
            new Posting(ChartOfAccounts.client(toAccountId), LedgerSide.CREDIT, amount));
    }

    /** O trade liquida: o comprador paga o vendedor. O dinheiro ja estava dentro. */
    public static LedgerTransaction trade(String reference, String buyAccountId, String sellAccountId,
                                          BigDecimal amount, Instant occurredAt) {
        return of(reference, LedgerReason.TRADE, occurredAt,
            new Posting(ChartOfAccounts.client(buyAccountId), LedgerSide.DEBIT, amount),
            new Posting(ChartOfAccounts.client(sellAccountId), LedgerSide.CREDIT, amount));
    }

    /**
     * A conta nasce com saldo: e um deposito no instante da abertura. A conta em si nasce vazia --
     * o saldo dela e a projecao das partidas, nao um numero que veio junto com o cadastro.
     */
    public static LedgerTransaction opening(String reference, String accountId, BigDecimal amount,
                                            Instant occurredAt) {
        return of(reference, LedgerReason.OPENING, occurredAt,
            new Posting(ChartOfAccounts.SETTLEMENT, LedgerSide.DEBIT, amount),
            new Posting(ChartOfAccounts.client(accountId), LedgerSide.CREDIT, amount));
    }

    private static LedgerTransaction of(String reference, LedgerReason reason, Instant occurredAt,
                                        Posting... postings) {
        return new LedgerTransaction(reference, reason, occurredAt, List.of(postings));
    }

    private static BigDecimal total(List<Posting> postings, LedgerSide side) {
        return postings.stream()
            .filter(posting -> posting.side() == side)
            .map(Posting::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
