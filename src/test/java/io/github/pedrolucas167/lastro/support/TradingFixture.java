package io.github.pedrolucas167.lastro.support;

import io.github.pedrolucas167.lastro.config.PriceBandProperties;
import io.github.pedrolucas167.lastro.domain.Actor;
import io.github.pedrolucas167.lastro.domain.MatchingEngine;
import io.github.pedrolucas167.lastro.journal.EventStore;
import io.github.pedrolucas167.lastro.journal.JournalReplay;
import io.github.pedrolucas167.lastro.ledger.LedgerService;
import io.github.pedrolucas167.lastro.metrics.MetricsCollector;
import io.github.pedrolucas167.lastro.service.AccountService;
import io.github.pedrolucas167.lastro.service.ClientService;
import io.github.pedrolucas167.lastro.service.CustodyService;
import io.github.pedrolucas167.lastro.service.MarketHaltService;
import io.github.pedrolucas167.lastro.service.OrderService;
import io.github.pedrolucas167.lastro.service.PositionService;
import io.github.pedrolucas167.lastro.service.PreTradeRiskService;
import io.github.pedrolucas167.lastro.service.RegulatoryValidationService;
import io.github.pedrolucas167.lastro.service.TradingService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Monta os servicos em memoria para os testes que nao sobem o contexto Spring.
 *
 * Semeia o lastro que as ordens exigem: caixa para comprar, acao em custodia para vender. O grafo e
 * novo a cada instancia, com o journal compartilhado -- e assim que um teste simula reinicio.
 */
public final class TradingFixture {
    public static final Actor BACK_OFFICE = new Actor("operador", Set.of(Actor.OPERATOR));

    public final EventStore eventStore;
    public final PriceBandProperties priceBand;
    public final MatchingEngine engine = new MatchingEngine();
    public final MetricsCollector metrics = new MetricsCollector();
    public final PositionService positions = new PositionService();
    public final LedgerService ledger = new LedgerService();
    public final ClientService clients;
    public final OrderService orders;
    public final AccountService accounts;
    public final RegulatoryValidationService validation;
    public final PreTradeRiskService risk;
    public final CustodyService custody;
    public final TradingService trading;

    public TradingFixture(EventStore eventStore) {
        this(eventStore, bands(Set.of()));
    }

    public TradingFixture(EventStore eventStore, PriceBandProperties priceBand) {
        this.eventStore = eventStore;
        this.priceBand = priceBand;
        this.orders = new OrderService(eventStore);
        this.clients = new ClientService(eventStore);
        this.accounts = new AccountService(eventStore, ledger);
        this.validation = new RegulatoryValidationService(new MarketHaltService(eventStore), priceBand,
            false, true, "00:00", "23:59");
        this.risk = new PreTradeRiskService(accounts, positions, priceBand, clients);
        this.custody = new CustodyService(accounts, positions, validation, eventStore);
        this.trading = new TradingService(engine, orders, positions, accounts, metrics, validation,
            risk, eventStore);
    }

    /** Banda de preco habilitada para os simbolos dados, com referencia em cada um. */
    public static PriceBandProperties bands(Set<String> symbols) {
        PriceBandProperties priceBand = new PriceBandProperties();
        symbols.forEach(symbol -> priceBand.getReferencePrices().put(symbol, new BigDecimal("30.00")));
        return priceBand;
    }

    /** Reinicio: grafo novo sobre o mesmo journal, reconstruido na ordem do startup. */
    public TradingFixture restart() {
        TradingFixture fresh = new TradingFixture(eventStore, priceBand);
        new JournalReplay(eventStore, List.of(fresh.clients, fresh.accounts, fresh.trading)).run(null);
        return fresh;
    }

    /** Conta com saldo: o lastro da compra. */
    public String fundedAccount(String balance) {
        return accounts.createAccount(BACK_OFFICE, new BigDecimal(balance)).getId();
    }

    /** Conta nova com acao em custodia: o lastro da venda. */
    public String accountWithShares(String balance, String symbol, int quantity, String price) {
        String accountId = fundedAccount(balance);
        custody.deposit(BACK_OFFICE, accountId, symbol, quantity, new BigDecimal(price));
        return accountId;
    }
}
