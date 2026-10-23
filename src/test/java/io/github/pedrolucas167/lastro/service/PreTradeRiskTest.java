package io.github.pedrolucas167.lastro.service;

import io.github.pedrolucas167.lastro.domain.Client;
import io.github.pedrolucas167.lastro.domain.ClientLimits;
import io.github.pedrolucas167.lastro.domain.Order;
import io.github.pedrolucas167.lastro.domain.OrderSide;
import io.github.pedrolucas167.lastro.domain.OrderStatus;
import io.github.pedrolucas167.lastro.domain.OrderType;
import io.github.pedrolucas167.lastro.domain.TimeInForce;
import io.github.pedrolucas167.lastro.exception.ClientLimitExceededException;
import io.github.pedrolucas167.lastro.exception.RegulatoryException;
import io.github.pedrolucas167.lastro.journal.InMemoryEventStore;
import io.github.pedrolucas167.lastro.journal.OrderSubmitted;
import io.github.pedrolucas167.lastro.support.TradingFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pre-trade: nenhuma ordem chega ao engine sem lastro. Compra precisa do caixa, venda precisa da
 * acao -- e o que executou vira pagamento, o que nao executou volta ao disponivel.
 */
class PreTradeRiskTest {
    private static final String SYMBOL = "PETR4";

    @Test
    @DisplayName("Compra sem caixa e recusada antes de virar fato no journal")
    void buyWithoutCashLeavesNoTrace() {
        TradingFixture fixture = fixture();
        String poor = fixture.fundedAccount("100.00");

        assertThrows(RegulatoryException.class, () -> buy(fixture, poor, "38.50", 10, TimeInForce.GTC));

        assertEquals(0, journaled(fixture));
        assertEquals(0, fixture.trading.getOrderBook(SYMBOL).getTotalBidQuantity());
    }

    @Test
    @DisplayName("A ordem em aberto bloqueia o caixa: sobra para gastar o que nao esta prometido")
    void anOpenBuyHoldsTheCashItCanStillSpend() {
        TradingFixture fixture = fixture();
        String account = fixture.fundedAccount("1000.00");

        buy(fixture, account, "30.00", 10, TimeInForce.GTC);

        assertEquals(0, new BigDecimal("700.00").compareTo(fixture.accounts.getAvailable(account)));

        // 710 nao cabe nos 700 que sobraram -- e a tentativa recusada nao bloqueia nada.
        assertThrows(RegulatoryException.class, () -> buy(fixture, account, "71.00", 10, TimeInForce.GTC));
        assertEquals(0, new BigDecimal("700.00").compareTo(fixture.accounts.getAvailable(account)));
        assertEquals(0, new BigDecimal("1000.00").compareTo(fixture.accounts.getAccount(account).getBalance()));
    }

    @Test
    @DisplayName("Venda sem a acao e recusada: nao ha posicao descoberta")
    void sellWithoutSharesLeavesNoTrace() {
        TradingFixture fixture = fixture();
        String account = fixture.fundedAccount("100000.00");

        assertThrows(RegulatoryException.class, () -> sell(fixture, account, "30.00", 10, TimeInForce.GTC));

        assertEquals(0, journaled(fixture));
        assertEquals(0, fixture.trading.getOrderBook(SYMBOL).getTotalAskQuantity());
    }

    @Test
    @DisplayName("A mesma acao nao e prometida duas vezes")
    void theSameSharesCannotBeSoldTwice() {
        TradingFixture fixture = fixture();
        String seller = fixture.accountWithShares("0.00", SYMBOL, 10, "30.00");

        sell(fixture, seller, "30.00", 10, TimeInForce.GTC);

        assertEquals(0, fixture.positions.getAvailable(seller, SYMBOL));
        assertThrows(RegulatoryException.class, () -> sell(fixture, seller, "30.00", 1, TimeInForce.GTC));
        assertEquals(1, journaled(fixture));
    }

    @Test
    @DisplayName("O trade liquida: o comprador paga e o vendedor recebe na hora")
    void theTradeMovesTheCash() {
        TradingFixture fixture = fixture();
        String seller = fixture.accountWithShares("0.00", SYMBOL, 100, "30.00");
        String buyer = fixture.fundedAccount("10000.00");

        sell(fixture, seller, "30.00", 100, TimeInForce.GTC);
        buy(fixture, buyer, "30.00", 40, TimeInForce.GTC);

        assertEquals(0, new BigDecimal("8800.00").compareTo(fixture.accounts.getAccount(buyer).getBalance()));
        assertEquals(0, new BigDecimal("1200.00").compareTo(fixture.accounts.getAccount(seller).getBalance()));

        assertEquals(40, fixture.positions.getQuantity(buyer, SYMBOL));
        assertEquals(60, fixture.positions.getQuantity(seller, SYMBOL));

        // O que executou deixou de ser bloqueio; o que sobrou da venda segue prometido.
        assertEquals(0, new BigDecimal("8800.00").compareTo(fixture.accounts.getAvailable(buyer)));
        assertEquals(0, fixture.positions.getAvailable(seller, SYMBOL));
        assertEquals(60, fixture.trading.getOrderBook(SYMBOL).getTotalAskQuantity());
    }

    @Test
    @DisplayName("Cancelar a ordem devolve o lastro")
    void cancellingReleasesTheHold() {
        TradingFixture fixture = fixture();
        String account = fixture.fundedAccount("1000.00");
        Order order = buy(fixture, account, "30.00", 10, TimeInForce.GTC);

        fixture.trading.cancelOrder(TradingFixture.BACK_OFFICE, order.getId(), SYMBOL);

        assertEquals(0, new BigDecimal("1000.00").compareTo(fixture.accounts.getAvailable(account)));
    }

    @Test
    @DisplayName("Compra a mercado bloqueia o teto da banda e executa no preco de quem vendeu")
    void marketBuyIsBoundedByTheBandCeiling() {
        TradingFixture fixture = new TradingFixture(new InMemoryEventStore(),
            TradingFixture.bands(Set.of(SYMBOL)));
        String seller = fixture.accountWithShares("0.00", SYMBOL, 100, "32.00");
        String buyer = fixture.fundedAccount("10000.00");

        sell(fixture, seller, "32.00", 100, TimeInForce.GTC);

        // Referencia 30.00 com banda de 10%: o teto e 33.00, e a compra a mercado e aceita.
        OrderExecutionResult result = fixture.trading.submitOrder(TradingFixture.BACK_OFFICE,
            SYMBOL, OrderSide.BUY, OrderType.MARKET, null, 60, buyer, TimeInForce.GTC);

        assertEquals(OrderStatus.FILLED, result.order().getStatus());
        assertEquals(1, result.trades().size());
        assertEquals(0, new BigDecimal("32.00").compareTo(result.trades().getFirst().getPrice()));

        // Pagou 60 x 32.00 e o resto do bloqueio voltou: nao fica dinheiro parado.
        assertEquals(0, new BigDecimal("8080.00").compareTo(fixture.accounts.getAccount(buyer).getBalance()));
        assertEquals(0, new BigDecimal("8080.00").compareTo(fixture.accounts.getAvailable(buyer)));
        assertTrue(fixture.trading.getOrderBook(SYMBOL).getBestBid().isEmpty());
    }

    @Test
    @DisplayName("A mercado sem liquidez nao repousa: o que nao executou e cancelado")
    void marketOrderWithoutLiquidityDoesNotRest() {
        TradingFixture fixture = new TradingFixture(new InMemoryEventStore(),
            TradingFixture.bands(Set.of(SYMBOL)));
        String seller = fixture.accountWithShares("0.00", SYMBOL, 100, "30.00");
        String buyer = fixture.fundedAccount("10000.00");

        sell(fixture, seller, "30.00", 40, TimeInForce.GTC);

        OrderExecutionResult result = fixture.trading.submitOrder(TradingFixture.BACK_OFFICE,
            SYMBOL, OrderSide.BUY, OrderType.MARKET, null, 100, buyer, TimeInForce.GTC);

        assertEquals(OrderStatus.CANCELLED, result.order().getStatus());
        assertEquals(40, result.trades().getFirst().getQuantity());
        assertTrue(fixture.trading.getOrderBook(SYMBOL).getBestBid().isEmpty());

        // O que nao executou voltou para o disponivel.
        assertEquals(0, new BigDecimal("8800.00").compareTo(fixture.accounts.getAvailable(buyer)));
    }

    @Test
    @DisplayName("Compra a mercado em simbolo sem banda de referencia e recusada")
    void marketBuyWithoutABandReferenceIsRefused() {
        TradingFixture fixture = fixture();
        String account = fixture.fundedAccount("10000.00");

        assertThrows(RegulatoryException.class, () -> fixture.trading.submitOrder(
            TradingFixture.BACK_OFFICE, SYMBOL, OrderSide.BUY, OrderType.MARKET, null, 10, account,
            TimeInForce.GTC));

        assertEquals(0, journaled(fixture));
    }

    @Test
    @DisplayName("O amend bloqueia so o que ele passa a pedir a mais")
    void amendHoldsOnlyTheExtra() {
        TradingFixture fixture = fixture();
        String account = fixture.fundedAccount("1000.00");
        Order order = buy(fixture, account, "30.00", 10, TimeInForce.GTC);

        // 11 x 30.00 = 330: mais 30 bloqueados.
        assertTrue(fixture.trading.replaceOrder(TradingFixture.BACK_OFFICE, order.getId(), SYMBOL,
            new BigDecimal("30.00"), 11).isPresent());
        assertEquals(0, new BigDecimal("670.00").compareTo(fixture.accounts.getAvailable(account)));

        // 34 x 30.00 = 1020 nao cabe: o amend e recusado e o bloqueio extra nao fica.
        assertThrows(RegulatoryException.class, () -> fixture.trading.replaceOrder(
            TradingFixture.BACK_OFFICE, order.getId(), SYMBOL, new BigDecimal("30.00"), 34));
        assertEquals(0, new BigDecimal("670.00").compareTo(fixture.accounts.getAvailable(account)));

        // Reduzir devolve o que a ordem deixou de pedir.
        assertTrue(fixture.trading.replaceOrder(TradingFixture.BACK_OFFICE, order.getId(), SYMBOL,
            new BigDecimal("30.00"), 5).isPresent());
        assertEquals(0, new BigDecimal("850.00").compareTo(fixture.accounts.getAvailable(account)));
    }

    @Test
    @DisplayName("O teto por ordem barra a compra acima dele e nao alcanca a venda")
    void orderLimitRefusesTheBuyAboveTheCeiling() {
        TradingFixture fixture = fixture();
        String subject = "cliente-limite";
        Client client = fixture.clients.register(TradingFixture.BACK_OFFICE, subject, "Maria");
        fixture.clients.setLimits(TradingFixture.BACK_OFFICE, client.getId(),
            new ClientLimits(new BigDecimal("500.00"), null));
        String buyer = fixture.accounts.createAccount(TradingFixture.BACK_OFFICE,
            new BigDecimal("1000.00"), subject).getId();

        // 30 x 20 = 600 acima do teto: nao vira fato e nao bloqueia caixa.
        assertThrows(ClientLimitExceededException.class,
            () -> buy(fixture, buyer, "30.00", 20, TimeInForce.GTC));
        assertEquals(0, journaled(fixture));
        assertEquals(0, new BigDecimal("1000.00").compareTo(fixture.accounts.getAvailable(buyer)));

        // 30 x 10 = 300 cabe no teto.
        assertTrue(buy(fixture, buyer, "30.00", 10, TimeInForce.GTC).isOpen());

        // Venda nao compromete caixa: 100 x 30 = 3000 passa pelo mesmo teto de 500.
        String seller = fixture.accounts.createAccount(TradingFixture.BACK_OFFICE,
            new BigDecimal("0.00"), subject).getId();
        fixture.custody.deposit(TradingFixture.BACK_OFFICE, seller, SYMBOL, 100,
            new BigDecimal("30.00"));

        assertTrue(sell(fixture, seller, "30.00", 100, TimeInForce.GTC).isOpen());
    }

    @Test
    @DisplayName("O replay reconstroi o bloqueio a partir das ordens em aberto")
    void replayRebuildsTheHold() {
        TradingFixture fixture = fixture();
        String buyer = fixture.fundedAccount("1000.00");
        String seller = fixture.accountWithShares("0.00", SYMBOL, 100, "30.00");

        buy(fixture, buyer, "30.00", 10, TimeInForce.GTC);
        sell(fixture, seller, "30.00", 100, TimeInForce.GTC);

        TradingFixture replayed = fixture.restart();

        assertEquals(0, new BigDecimal("700.00").compareTo(replayed.accounts.getAvailable(buyer)));
        assertEquals(0, replayed.positions.getAvailable(seller, SYMBOL));

        // O dinheiro do trade que executou tambem volta do journal.
        assertEquals(0, new BigDecimal("700.00").compareTo(replayed.accounts.getAccount(buyer).getBalance()));
        assertEquals(0, new BigDecimal("300.00").compareTo(replayed.accounts.getAccount(seller).getBalance()));
    }

    private TradingFixture fixture() {
        return new TradingFixture(new InMemoryEventStore());
    }

    private Order buy(TradingFixture fixture, String accountId, String price, int quantity,
                      TimeInForce timeInForce) {
        return fixture.trading.submitOrder(TradingFixture.BACK_OFFICE, SYMBOL, OrderSide.BUY,
            OrderType.LIMIT, new BigDecimal(price), quantity, accountId, timeInForce).order();
    }

    private Order sell(TradingFixture fixture, String accountId, String price, int quantity,
                       TimeInForce timeInForce) {
        return fixture.trading.submitOrder(TradingFixture.BACK_OFFICE, SYMBOL, OrderSide.SELL,
            OrderType.LIMIT, new BigDecimal(price), quantity, accountId, timeInForce).order();
    }

    private long journaled(TradingFixture fixture) {
        return fixture.eventStore.readAll().stream().filter(OrderSubmitted.class::isInstance).count();
    }
}
