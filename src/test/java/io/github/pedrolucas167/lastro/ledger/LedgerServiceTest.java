package io.github.pedrolucas167.lastro.ledger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    private LedgerService ledger;

    @BeforeEach
    void setUp() {
        ledger = new LedgerService();
    }

    @Test
    void postsDepositWithSettlementOnTheOtherSide() {
        ledger.post(LedgerTransaction.deposit("dep-1", "conta-1", new BigDecimal("100.00"), NOW));

        assertEquals(0, new BigDecimal("100.00").compareTo(ledger.clientBalance("conta-1")));
        assertEquals(0, new BigDecimal("100.00").compareTo(ledger.settlementBalance()));
        assertTrue(ledger.isBalanced());
        assertEquals(2, ledger.trialBalance().size());
    }

    @Test
    void refusesUnbalancedTransaction() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
            new LedgerTransaction("sem-contrapartida", LedgerReason.TRADE, NOW, List.of(
                new Posting(ChartOfAccounts.client("a"), LedgerSide.DEBIT, new BigDecimal("100.00")),
                new Posting(ChartOfAccounts.client("b"), LedgerSide.CREDIT, new BigDecimal("90.00")))));

        assertTrue(error.getMessage().contains("Unbalanced"), error.getMessage());
    }

    @Test
    void refusesTransactionWithASinglePosting() {
        assertThrows(IllegalArgumentException.class, () -> new LedgerTransaction("so-um-lado",
            LedgerReason.DEPOSIT, NOW, List.of(
                new Posting(ChartOfAccounts.SETTLEMENT, LedgerSide.DEBIT, new BigDecimal("1.00")))));
    }

    @Test
    void refusesPostingOfZeroOrNegative() {
        assertThrows(IllegalArgumentException.class, () -> new Posting(ChartOfAccounts.SETTLEMENT,
            LedgerSide.DEBIT, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new Posting(ChartOfAccounts.SETTLEMENT,
            LedgerSide.DEBIT, new BigDecimal("-1.00")));
    }

    @Test
    void statementHidesTheOtherClientAndKeepsTheRunningBalance() {
        ledger.post(LedgerTransaction.deposit("dep-1", "a", new BigDecimal("100.00"), NOW));
        ledger.post(LedgerTransaction.trade("trade-1", "a", "b", new BigDecimal("30.00"), NOW));

        List<LedgerLine> bought = ledger.statementOf("a");
        assertEquals(2, bought.size());
        LedgerLine debited = bought.get(1);
        assertEquals(LedgerReason.TRADE, debited.reason());
        assertEquals(LedgerSide.DEBIT, debited.side());
        assertEquals(ChartOfAccounts.CLIENT_LIABILITY, debited.counterparty());
        assertEquals(0, new BigDecimal("70.00").compareTo(debited.balanceAfter()));

        LedgerLine credited = ledger.statementOf("b").getFirst();
        assertEquals("trade-1", credited.reference());
        assertEquals(LedgerSide.CREDIT, credited.side());
        assertEquals(0, new BigDecimal("30.00").compareTo(credited.balanceAfter()));
    }

    @Test
    void clearForgetsTheBook() {
        ledger.post(LedgerTransaction.deposit("dep-1", "a", new BigDecimal("100.00"), NOW));

        ledger.clear();

        assertEquals(0, BigDecimal.ZERO.compareTo(ledger.clientBalance("a")));
        assertTrue(ledger.trialBalance().isEmpty());
        assertTrue(ledger.statementOf("a").isEmpty());
    }
}
