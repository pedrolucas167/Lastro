package io.github.pedrolucas167.lastro.service;

import io.github.pedrolucas167.lastro.domain.Account;
import io.github.pedrolucas167.lastro.domain.Actor;
import io.github.pedrolucas167.lastro.exception.AccountAccessDeniedException;
import io.github.pedrolucas167.lastro.exception.AccountAlreadyHasHolderException;
import io.github.pedrolucas167.lastro.exception.AccountNotFoundException;
import io.github.pedrolucas167.lastro.exception.InsufficientFundsException;
import io.github.pedrolucas167.lastro.journal.AccountHolderAssigned;
import io.github.pedrolucas167.lastro.journal.AccountOpened;
import io.github.pedrolucas167.lastro.journal.DomainEvent;
import io.github.pedrolucas167.lastro.journal.EventStore;
import io.github.pedrolucas167.lastro.journal.FundsCredited;
import io.github.pedrolucas167.lastro.journal.FundsDebited;
import io.github.pedrolucas167.lastro.journal.JournalRebuildable;
import io.github.pedrolucas167.lastro.journal.MoneyEvent;
import io.github.pedrolucas167.lastro.journal.TradeExecuted;
import io.github.pedrolucas167.lastro.journal.TransferCompleted;
import io.github.pedrolucas167.lastro.ledger.ChartOfAccounts;
import io.github.pedrolucas167.lastro.ledger.LedgerReason;
import io.github.pedrolucas167.lastro.ledger.LedgerService;
import io.github.pedrolucas167.lastro.ledger.LedgerSide;
import io.github.pedrolucas167.lastro.ledger.LedgerTransaction;
import io.github.pedrolucas167.lastro.ledger.Posting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * O caixa dos clientes: cada movimento e um fato no journal e duas partidas no livro-razao.
 *
 * O saldo de uma conta nao e um numero proprio -- e a projecao do codigo dela no plano de contas
 * ({@code LIABILITIES:CLIENT:<id>}). Por isso o livro e a conta nunca podem divergir: os dois saem
 * das mesmas partidas, e o replay reconstroi os dois do mesmo jeito.
 */
@Service
@Order(1)
public class AccountService implements JournalRebuildable {
    private static final Logger logger = LoggerFactory.getLogger(AccountService.class);

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final EventStore eventStore;
    private final LedgerService ledger;

    public AccountService(EventStore eventStore, LedgerService ledger) {
        this.eventStore = eventStore;
        this.ledger = ledger;
    }

    public Account createAccount(Actor actor, BigDecimal initialBalance) {
        return createAccount(actor, initialBalance, null);
    }

    public Account createAccount(Actor actor, BigDecimal initialBalance, String holder) {
        String id = UUID.randomUUID().toString();
        Instant at = Instant.now();
        // A conta nasce vazia: o saldo inicial entra como abertura, pela mesma porta de um deposito.
        Account account = new Account(id, BigDecimal.ZERO);
        accounts.put(id, account);
        try {
            record(new AccountOpened(id, initialBalance, actor, at),
                movement(initialBalance, () -> LedgerTransaction.opening(id, id, initialBalance, at)));
        } catch (RuntimeException e) {
            accounts.remove(id);
            throw e;
        }
        if (holder != null) {
            assignHolder(actor, id, holder);
        }
        logger.info("Created account {} with initial balance {} and holder {}", id, initialBalance, holder);
        return account;
    }

    /** Da um titular a uma conta que ainda nao tem: o vinculo e definido uma vez. */
    public Account assignHolder(Actor actor, String accountId, String holder) {
        Account account = getAccount(accountId);
        if (account.getHolder() != null) {
            throw new AccountAlreadyHasHolderException(accountId);
        }
        eventStore.append(new AccountHolderAssigned(accountId, holder, actor, Instant.now()));
        account.assignHolder(holder);
        logger.info("Assigned account {} to holder {}", accountId, holder);
        return account;
    }

    public Account getAccount(String accountId) {
        Account account = accounts.get(accountId);
        if (account == null) {
            logger.warn("Account not found: {}", accountId);
            throw new AccountNotFoundException(accountId);
        }
        return account;
    }

    /** A conta, se o ator pode enxerga-la; tirar dinheiro dela passa por aqui. */
    public Account getAccountFor(Actor actor, String accountId) {
        Account account = getAccount(accountId);
        if (!actor.privileged() && !account.heldBy(actor.subject())) {
            throw new AccountAccessDeniedException("Account " + accountId + " belongs to another holder");
        }
        return account;
    }

    public boolean canAccess(Actor actor, String accountId) {
        Account account = accounts.get(accountId);
        return account != null && (actor.privileged() || account.heldBy(actor.subject()));
    }

    /** As contas do titular, em ordem estavel de id. */
    public List<Account> accountsOf(String holder) {
        return accounts.values().stream()
            .filter(account -> account.heldBy(holder))
            .sorted(Comparator.comparing(Account::getId))
            .toList();
    }

    public Account credit(Actor actor, String accountId, BigDecimal amount) {
        Account account = getAccount(accountId);
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        Instant at = Instant.now();
        record(new FundsCredited(accountId, amount, actor, at),
            movement(amount, () -> LedgerTransaction.deposit(accountId, accountId, amount, at)));
        logger.info("Credited {} to account {}", amount, accountId);
        return account;
    }

    public Account debit(Actor actor, String accountId, BigDecimal amount) {
        Account account = getAccount(accountId);
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        Instant at = Instant.now();
        record(new FundsDebited(accountId, amount, actor, at),
            movement(amount, () -> LedgerTransaction.withdrawal(accountId, accountId, amount, at)));
        logger.info("Debited {} from account {}", amount, accountId);
        return account;
    }

    /**
     * Move o dinheiro da transferencia e grava o fato. O ator do fato e quem manda: tirar dinheiro
     * de conta que ele nao tem e 403, e nao chega ao journal.
     *
     * Devolve false quando o disponivel da origem nao cobre -- nada foi movido nem gravado.
     */
    public boolean transfer(TransferCompleted fact) {
        getAccountFor(fact.actor(), fact.fromAccountId());
        getAccount(fact.toAccountId());
        try {
            record(fact, movement(fact.amount(), () -> LedgerTransaction.transfer(fact.transactionId(),
                fact.fromAccountId(), fact.toAccountId(), fact.amount(), fact.occurredAt())));
            return true;
        } catch (InsufficientFundsException e) {
            return false;
        }
    }

    /**
     * Bloqueia valor para uma ordem de compra. Devolve false quando o disponivel nao cobre; quem
     * chamou decide o que dizer ao cliente. O bloqueio nao e evento do journal: e consequencia das
     * ordens em aberto, e o replay o reconstroi somando-as.
     */
    public boolean hold(String accountId, BigDecimal amount) {
        Account account = getAccount(accountId);
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            return true;
        }
        if (!account.hold(amount)) {
            logger.warn("Insufficient buying power in account {}: needs {}, available {}",
                accountId, amount, account.getAvailable());
            return false;
        }
        logger.info("Held {} in account {}", amount, accountId);
        return true;
    }

    /** Devolve ao disponivel o valor bloqueado que a ordem nao vai mais gastar. */
    public void release(String accountId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        getAccount(accountId).release(amount);
        logger.info("Released {} in account {}", amount, accountId);
    }

    /** Restaura o bloqueado no replay, a partir da soma das ordens de compra em aberto. */
    public void rebuildHeld(String accountId, BigDecimal amount) {
        getAccount(accountId).rebuildHeld(amount);
    }

    /** O disponivel da conta: o saldo menos o bloqueado para ordens de compra. */
    public BigDecimal getAvailable(String accountId) {
        return getAccount(accountId).getAvailable();
    }

    /**
     * O trade e o fato e o caixa e consequencia: o comprador paga preco x quantidade e o vendedor
     * recebe, no mesmo momento. O {@link TradeExecuted} ja esta no journal -- quem registrou o trade
     * o gravou --, entao aqui so a conta e o livro sao movidos: o caminho vivo e o replay passam
     * pelo mesmo codigo, e o dinheiro nao muda de lugar sem a trilha.
     */
    public void settleTrade(TradeExecuted trade) {
        BigDecimal amount = trade.price().multiply(BigDecimal.valueOf(trade.quantity()));
        applyLedger(LedgerTransaction.trade(trade.tradeId(), trade.buyAccountId(),
            trade.sellAccountId(), amount, trade.occurredAt()));
        logger.info("Settled trade {}: {} paid {} to {}", trade.tradeId(), trade.buyAccountId(), amount,
            trade.sellAccountId());
    }

    /** Reconstroi o estado a partir do journal, sem regravar eventos. */
    public void rebuild(List<DomainEvent> events) {
        accounts.clear();
        ledger.clear();
        for (DomainEvent event : events) {
            if (event instanceof MoneyEvent moneyEvent) {
                replayEvent(moneyEvent);
            } else if (event instanceof TradeExecuted trade) {
                rebuildTrade(trade);
            }
        }
        reconcile();
    }

    /**
     * A prova do livro na subida: cada conta tem exatamente o saldo do seu codigo no plano. Um
     * journal que reconstrua um sem o outro e bug -- e e melhor nao subir do que subir com caixa
     * divergente.
     */
    private void reconcile() {
        for (Account account : accounts.values()) {
            BigDecimal book = ledger.clientBalance(account.getId());
            if (book.compareTo(account.getBalance()) != 0) {
                throw new IllegalStateException("Ledger and account diverge for " + account.getId()
                    + ": book " + book + ", account " + account.getBalance());
            }
        }
        logger.info("Ledger reconciled: {} client accounts, liabilities {}, settlement {}",
            accounts.size(), ledger.clientLiabilitiesTotal(), ledger.settlementBalance());
    }

    /**
     * O trade do journal liquida as contas que ele cita. Um journal anterior ao lastro pode citar
     * conta que nunca foi aberta -- a API aceitava qualquer id --, e nao ha onde lancar o caixa
     * desse lado: inventar a conta (e o dinheiro) seria pior que dizer o que ficou de fora.
     */
    private void rebuildTrade(TradeExecuted trade) {
        if (!hasAccount(trade.buyAccountId()) || !hasAccount(trade.sellAccountId())) {
            logger.warn("Trade {} cites an account the journal never opened ({} pays {}): "
                + "cash not settled", trade.tradeId(), trade.buyAccountId(), trade.sellAccountId());
            return;
        }
        settleTrade(trade);
    }

    /** Se a conta existe no que o journal reconstruiu; sem lancar quando nao existe. */
    public boolean hasAccount(String accountId) {
        return accounts.containsKey(accountId);
    }

    /**
     * O fato: projeta na conta, grava no journal e lanca no livro. A conta vem primeiro porque e
     * nela que o debito sem lastro e recusado -- fato recusado nao vira trilha. Se a gravacao falhar,
     * a conta volta ao que era: memoria que andou sem journal seria divergencia ate o proximo replay.
     */
    private void record(DomainEvent fact, LedgerTransaction transaction) {
        List<Runnable> undo = project(transaction);
        try {
            eventStore.append(fact);
            if (transaction != null) {
                ledger.post(transaction);
            }
        } catch (RuntimeException e) {
            undo.reversed().forEach(Runnable::run);
            throw e;
        }
    }

    /**
     * O fato que nao move dinheiro -- saldo inicial zero, ajuste de zero -- nao tem partida: o
     * journal registra o que aconteceu, e o livro nao se move. Partida de zero nao existe.
     */
    private static LedgerTransaction movement(BigDecimal amount, Supplier<LedgerTransaction> movement) {
        return amount.signum() > 0 ? movement.get() : null;
    }

    /** O replay: a conta e o livro saem do fato que ja esta no journal. */
    private void replayEvent(MoneyEvent event) {
        switch (event) {
            case AccountOpened e -> {
                accounts.put(e.accountId(), new Account(e.accountId(), BigDecimal.ZERO));
                applyLedger(movement(e.initialBalance(), () -> LedgerTransaction.opening(e.accountId(),
                    e.accountId(), e.initialBalance(), e.occurredAt())));
            }
            case AccountHolderAssigned e -> account(e.accountId()).assignHolder(e.holder());
            case FundsCredited e -> applyLedger(movement(e.amount(), () -> LedgerTransaction.deposit(
                e.accountId(), e.accountId(), e.amount(), e.occurredAt())));
            case FundsDebited e -> applyLedger(movement(e.amount(), () -> LedgerTransaction.withdrawal(
                e.accountId(), e.accountId(), e.amount(), e.occurredAt())));
            case TransferCompleted e -> applyLedger(movement(e.amount(), () -> LedgerTransaction.transfer(
                e.transactionId(), e.fromAccountId(), e.toAccountId(), e.amount(), e.occurredAt())));
        }
    }

    private void applyLedger(LedgerTransaction transaction) {
        if (transaction == null) {
            return;
        }
        project(transaction);
        ledger.post(transaction);
    }

    /**
     * A contrapartida na conta. Devolve o que desfazer se o journal nao aceitar o fato -- a ordem
     * inversa das partidas, para credito e debito voltarem ao ponto de partida.
     */
    private List<Runnable> project(LedgerTransaction transaction) {
        List<Runnable> undo = new ArrayList<>();
        if (transaction == null) {
            return undo;
        }
        for (Posting posting : transaction.postings()) {
            if (!ChartOfAccounts.isClient(posting.accountCode())) {
                continue;
            }
            Account account = account(ChartOfAccounts.clientId(posting.accountCode()));
            if (posting.side() == LedgerSide.CREDIT) {
                account.credit(posting.amount());
                undo.add(() -> account.debit(posting.amount()));
            } else if (transaction.reason() == LedgerReason.TRADE) {
                // O valor ja estava bloqueado pela ordem: o trade debita o saldo, nao o disponivel.
                account.debit(posting.amount());
                undo.add(() -> account.credit(posting.amount()));
            } else if (account.tryDebit(posting.amount())) {
                undo.add(() -> account.credit(posting.amount()));
            } else {
                undo.reversed().forEach(Runnable::run);
                logger.warn("Insufficient funds for debit of {} from account {}", posting.amount(),
                    account.getId());
                throw new InsufficientFundsException(account.getId());
            }
        }
        return undo;
    }

    private Account account(String accountId) {
        Account account = accounts.get(accountId);
        if (account == null) {
            throw new AccountNotFoundException(accountId);
        }
        return account;
    }
}
