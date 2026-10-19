package io.github.pedrolucas167.lastro.service;

import io.github.pedrolucas167.lastro.domain.OrderSide;
import io.github.pedrolucas167.lastro.domain.OrderType;
import io.github.pedrolucas167.lastro.domain.TimeInForce;
import io.github.pedrolucas167.lastro.exception.AccountNotFoundException;
import io.github.pedrolucas167.lastro.exception.RegulatoryException;
import io.github.pedrolucas167.lastro.journal.CustodyTransferred;
import io.github.pedrolucas167.lastro.journal.InMemoryEventStore;
import io.github.pedrolucas167.lastro.support.TradingFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Custodia e por onde a acao entra no sistema e passa de uma conta para outra. Sem ela, uma conta
 * nova nao teria o que vender.
 */
class CustodyServiceTest {
    private static final String SYMBOL = "PETR4";

    @Test
    @DisplayName("O deposito poe a acao na conta e registra o fato")
    void depositPutsTheSharesInTheAccount() {
        TradingFixture fixture = fixture();
        String account = fixture.fundedAccount("0.00");

        CustodyTransfer transfer = fixture.custody.deposit(TradingFixture.BACK_OFFICE, account,
            SYMBOL, 100, new BigDecimal("30.00"));

        assertEquals(100, transfer.toQuantity());
        assertEquals(0, new BigDecimal("30.00").compareTo(transfer.price()));
        assertEquals(100, fixture.positions.getQuantity(account, SYMBOL));
        assertEquals(0, new BigDecimal("30.00").compareTo(fixture.positions.getPosition(account, SYMBOL).getAveragePrice()));
        assertEquals(1, journaled(fixture));
    }

    @Test
    @DisplayName("Deposito em conta que nao existe e recusado e nao deixa fato")
    void depositOnAnUnknownAccountIsRefused() {
        TradingFixture fixture = fixture();

        assertThrows(AccountNotFoundException.class, () -> fixture.custody.deposit(
            TradingFixture.BACK_OFFICE, "conta-inexistente", SYMBOL, 100, new BigDecimal("30.00")));

        assertEquals(0, journaled(fixture));
    }

    @Test
    @DisplayName("Sem preco medio o deposito e recusado: a acao entraria sem custo")
    void depositWithoutAPriceIsRefused() {
        TradingFixture fixture = fixture();
        String account = fixture.fundedAccount("0.00");

        assertThrows(RegulatoryException.class, () -> fixture.custody.deposit(
            TradingFixture.BACK_OFFICE, account, SYMBOL, 100, null));

        assertEquals(0, journaled(fixture));
    }

    @Test
    @DisplayName("A transferencia move a acao e o custo, e o destino faz a media")
    void transferMovesQuantityAndCost() {
        TradingFixture fixture = fixture();
        String origin = fixture.accountWithShares("0.00", SYMBOL, 100, "20.00");
        String destination = fixture.accountWithShares("0.00", SYMBOL, 100, "40.00");

        CustodyTransfer transfer = fixture.custody.transfer(TradingFixture.BACK_OFFICE, origin,
            destination, SYMBOL, 60);

        assertEquals(40, transfer.fromQuantity());
        assertEquals(160, transfer.toQuantity());
        assertEquals(0, new BigDecimal("20.00").compareTo(transfer.price()));

        // O que sai nao muda o preco medio de quem tinha -- só o que entra faz media no destino.
        assertEquals(0, new BigDecimal("20.00")
            .compareTo(fixture.positions.getPosition(origin, SYMBOL).getAveragePrice()));
        assertEquals(0, new BigDecimal("32.50")
            .compareTo(fixture.positions.getPosition(destination, SYMBOL).getAveragePrice()));
    }

    @Test
    @DisplayName("Nao se transfere mais acao do que a conta tem")
    void transferMoreThanTheAccountHasIsRefused() {
        TradingFixture fixture = fixture();
        String origin = fixture.accountWithShares("0.00", SYMBOL, 100, "30.00");
        String destination = fixture.fundedAccount("0.00");

        assertThrows(RegulatoryException.class, () -> fixture.custody.transfer(
            TradingFixture.BACK_OFFICE, origin, destination, SYMBOL, 101));

        assertEquals(100, fixture.positions.getQuantity(origin, SYMBOL));
        assertEquals(1, journaled(fixture));
    }

    @Test
    @DisplayName("Nao se transfere acao prometida a uma venda em aberto")
    void sharesHeldByAnOpenSellCannotBeTransferred() {
        TradingFixture fixture = fixture();
        String origin = fixture.accountWithShares("0.00", SYMBOL, 100, "30.00");
        String destination = fixture.fundedAccount("0.00");

        fixture.trading.submitOrder(TradingFixture.BACK_OFFICE, SYMBOL, OrderSide.SELL,
            OrderType.LIMIT, new BigDecimal("30.00"), 100, origin, TimeInForce.GTC);

        assertThrows(RegulatoryException.class, () -> fixture.custody.transfer(
            TradingFixture.BACK_OFFICE, origin, destination, SYMBOL, 100));
    }

    @Test
    @DisplayName("Transferir para a propria conta e recusado")
    void transferToTheSameAccountIsRefused() {
        TradingFixture fixture = fixture();
        String account = fixture.accountWithShares("0.00", SYMBOL, 100, "30.00");

        assertThrows(RegulatoryException.class, () -> fixture.custody.transfer(
            TradingFixture.BACK_OFFICE, account, account, SYMBOL, 10));
    }

    @Test
    @DisplayName("O replay devolve a custodia depositada e a transferida")
    void replayRestoresCustody() {
        TradingFixture fixture = fixture();
        String origin = fixture.accountWithShares("0.00", SYMBOL, 100, "30.00");
        String destination = fixture.fundedAccount("0.00");
        fixture.custody.transfer(TradingFixture.BACK_OFFICE, origin, destination, SYMBOL, 40);

        TradingFixture replayed = fixture.restart();

        assertEquals(60, replayed.positions.getQuantity(origin, SYMBOL));
        assertEquals(40, replayed.positions.getQuantity(destination, SYMBOL));
        assertEquals(0, new BigDecimal("30.00")
            .compareTo(replayed.positions.getPosition(origin, SYMBOL).getAveragePrice()));
    }

    private TradingFixture fixture() {
        return new TradingFixture(new InMemoryEventStore());
    }

    private long journaled(TradingFixture fixture) {
        return fixture.eventStore.readAll().stream()
            .filter(CustodyTransferred.class::isInstance)
            .count();
    }
}
