package io.github.pedrolucas167.lastro.ledger;

import io.github.pedrolucas167.lastro.domain.OrderSide;
import io.github.pedrolucas167.lastro.domain.OrderType;
import io.github.pedrolucas167.lastro.domain.TimeInForce;
import io.github.pedrolucas167.lastro.journal.InMemoryEventStore;
import io.github.pedrolucas167.lastro.journal.TradeExecuted;
import io.github.pedrolucas167.lastro.journal.TransferCompleted;
import io.github.pedrolucas167.lastro.exception.InsufficientFundsException;
import io.github.pedrolucas167.lastro.support.TradingFixture;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O livro e a conta sao a mesma coisa vista de dois angulos: o saldo do cliente e a projecao do
 * codigo dele no plano de contas. Estes testes cobram os dois lados de cada fato e a prova que fecha
 * o balanco -- a soma dos saldos dos clientes e o caixa na liquidante.
 */
class LedgerReconciliationTest {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @Test
    void depositPutsTheCashInSettlement() {
        TradingFixture fixture = new TradingFixture(new InMemoryEventStore());
        String account = fixture.fundedAccount("10000.00");

        assertEquals(0, new BigDecimal("10000.00").compareTo(fixture.ledger.clientBalance(account)));
        assertEquals(0, new BigDecimal("10000.00").compareTo(fixture.ledger.settlementBalance()));
        assertEquals(0, fixture.ledger.clientBalance(account)
            .compareTo(fixture.accounts.getAccount(account).getBalance()));
        assertTrue(fixture.ledger.isBalanced());
    }

    @Test
    void withdrawalTakesTheCashOutOfSettlement() {
        TradingFixture fixture = new TradingFixture(new InMemoryEventStore());
        String account = fixture.fundedAccount("100.00");

        fixture.accounts.debit(TradingFixture.BACK_OFFICE, account, new BigDecimal("40.00"));

        assertEquals(0, new BigDecimal("60.00").compareTo(fixture.ledger.clientBalance(account)));
        assertEquals(0, new BigDecimal("60.00").compareTo(fixture.ledger.settlementBalance()));
    }

    @Test
    void transferMovesCashBetweenClientsWithoutTouchingSettlement() {
        TradingFixture fixture = new TradingFixture(new InMemoryEventStore());
        String from = fixture.fundedAccount("100.00");
        String to = fixture.fundedAccount("0.00");

        boolean moved = fixture.accounts.transfer(new TransferCompleted("transfer-1", from, to,
            new BigDecimal("40.00"), null, TradingFixture.BACK_OFFICE, Instant.now()));

        assertTrue(moved);
        assertEquals(0, new BigDecimal("60.00").compareTo(fixture.ledger.clientBalance(from)));
        assertEquals(0, new BigDecimal("40.00").compareTo(fixture.ledger.clientBalance(to)));
        assertEquals(0, new BigDecimal("100.00").compareTo(fixture.ledger.settlementBalance()));
        assertEquals(0, fixture.ledger.clientLiabilitiesTotal().compareTo(fixture.ledger.settlementBalance()));
    }

    @Test
    void tradeSettlesBetweenClientsAndKeepsTheBalance() {
        TradingFixture fixture = new TradingFixture(new InMemoryEventStore());
        String seller = fixture.accountWithShares("0.00", "VALE3", 100, "30.00");
        String buyer = fixture.fundedAccount("10000.00");

        fixture.trading.submitOrder(TradingFixture.BACK_OFFICE, "VALE3", OrderSide.SELL,
            OrderType.LIMIT, new BigDecimal("30.00"), 100, seller, TimeInForce.GTC);
        fixture.trading.submitOrder(TradingFixture.BACK_OFFICE, "VALE3", OrderSide.BUY,
            OrderType.LIMIT, new BigDecimal("30.00"), 100, buyer, TimeInForce.GTC);

        assertEquals(0, new BigDecimal("7000.00").compareTo(fixture.ledger.clientBalance(buyer)));
        assertEquals(0, new BigDecimal("3000.00").compareTo(fixture.ledger.clientBalance(seller)));
        assertEquals(0, new BigDecimal("10000.00").compareTo(fixture.ledger.settlementBalance()));
        assertEquals(0, fixture.ledger.clientLiabilitiesTotal().compareTo(fixture.ledger.settlementBalance()));
        assertEquals(2, fixture.ledger.statementOf(buyer).size()); // a abertura e o trade
    }

    @Test
    void refusedDebitLeavesTheBookAndTheJournalUntouched() {
        InMemoryEventStore store = new InMemoryEventStore();
        TradingFixture fixture = new TradingFixture(store);
        String account = fixture.fundedAccount("100.00");

        assertThrows(InsufficientFundsException.class, () ->
            fixture.accounts.debit(TradingFixture.BACK_OFFICE, account, new BigDecimal("150.00")));

        assertEquals(0, new BigDecimal("100.00").compareTo(fixture.ledger.clientBalance(account)));
        assertEquals(0, new BigDecimal("100.00").compareTo(fixture.ledger.settlementBalance()));
        assertEquals(1, store.readAll().size()); // so a abertura
    }

    @Test
    void replayRebuildsTheSameBook() {
        TradingFixture fixture = new TradingFixture(new InMemoryEventStore());
        String seller = fixture.accountWithShares("0.00", "VALE3", 100, "30.00");
        String buyer = fixture.fundedAccount("10000.00");
        fixture.trading.submitOrder(TradingFixture.BACK_OFFICE, "VALE3", OrderSide.SELL,
            OrderType.LIMIT, new BigDecimal("30.00"), 100, seller, TimeInForce.GTC);
        fixture.trading.submitOrder(TradingFixture.BACK_OFFICE, "VALE3", OrderSide.BUY,
            OrderType.LIMIT, new BigDecimal("30.00"), 100, buyer, TimeInForce.GTC);
        fixture.accounts.transfer(new TransferCompleted("transfer-1", buyer, seller,
            new BigDecimal("500.00"), null, TradingFixture.BACK_OFFICE, Instant.now()));

        TradingFixture replayed = fixture.restart();

        assertEquals(0, replayed.ledger.clientBalance(buyer).compareTo(fixture.ledger.clientBalance(buyer)));
        assertEquals(0, replayed.ledger.clientBalance(seller).compareTo(fixture.ledger.clientBalance(seller)));
        assertEquals(0, replayed.ledger.settlementBalance().compareTo(fixture.ledger.settlementBalance()));
        assertEquals(fixture.ledger.statementOf(buyer), replayed.ledger.statementOf(buyer));
        assertEquals(fixture.ledger.trialBalance().size(), replayed.ledger.trialBalance().size());
    }

    /**
     * O journal antigo pode citar conta que nunca existiu: o trade nao liquida (nem na conta, nem no
     * livro), e o balanco continua fechando com o que realmente aconteceu.
     */
    @Test
    void orphanTradeFromAnOldJournalDoesNotMoveTheBook() {
        InMemoryEventStore store = new InMemoryEventStore();
        TradingFixture seed = new TradingFixture(store);
        String seller = seed.accountWithShares("0.00", "VALE3", 100, "30.00");
        store.append(new TradeExecuted("trade-orfao", "VALE3", "ordem-compra-orfa", "ordem-venda",
            new BigDecimal("30.50"), 100, "conta-que-nao-existe", seller,
            TradingFixture.BACK_OFFICE, Instant.now()));

        TradingFixture replayed = new TradingFixture(store).restart();

        assertEquals(0, ZERO.compareTo(replayed.ledger.clientBalance(seller)));
        assertEquals(0, ZERO.compareTo(replayed.ledger.settlementBalance()));
        assertTrue(replayed.ledger.statementOf(seller).isEmpty());
        assertTrue(replayed.ledger.isBalanced());
    }

    @Test
    void statementOfAnAccountWithoutMovementIsEmpty() {
        TradingFixture fixture = new TradingFixture(new InMemoryEventStore());
        String account = fixture.fundedAccount("0.00");

        List<LedgerLine> statement = fixture.ledger.statementOf(account);

        assertTrue(statement.isEmpty());
        assertEquals(0, ZERO.compareTo(fixture.ledger.clientBalance(account)));
    }
}
