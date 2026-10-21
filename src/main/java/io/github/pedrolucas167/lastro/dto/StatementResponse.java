package io.github.pedrolucas167.lastro.dto;

import io.github.pedrolucas167.lastro.domain.Account;
import io.github.pedrolucas167.lastro.ledger.LedgerLine;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** O extrato do caixa de uma conta: o saldo de agora e as partidas que o formaram. */
@Data
@AllArgsConstructor
public class StatementResponse {
    private String accountId;
    private BigDecimal balance;
    private List<Line> lines;

    public static StatementResponse of(Account account, List<LedgerLine> lines) {
        return new StatementResponse(account.getId(), account.getBalance(),
            lines.stream().map(Line::of).toList());
    }

    /** Uma partida do extrato. O lado e o do cliente: DEBIT saiu da conta, CREDIT entrou. */
    @Data
    @AllArgsConstructor
    public static class Line {
        /** O fato que causou a partida: id do trade, da transferencia, ou da conta na abertura. */
        private String reference;
        private String reason;
        private Instant occurredAt;
        private String side;
        private BigDecimal amount;
        /** A outra ponta; entre clientes vem sem o id, para nao revelar a conta do outro. */
        private String counterparty;
        private BigDecimal balanceAfter;

        static Line of(LedgerLine line) {
            return new Line(line.reference(), line.reason().name(), line.occurredAt(),
                line.side().name(), line.amount(), line.counterparty(), line.balanceAfter());
        }
    }
}
