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
import io.github.pedrolucas167.lastro.journal.TransferCompleted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AccountService implements JournalRebuildable {
    private static final Logger logger = LoggerFactory.getLogger(AccountService.class);
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final EventStore eventStore;

    public AccountService(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    public Account createAccount(Actor actor, BigDecimal initialBalance) {
        return createAccount(actor, initialBalance, null);
    }

    public Account createAccount(Actor actor, BigDecimal initialBalance, String holder) {
        String id = UUID.randomUUID().toString();
        eventStore.append(new AccountOpened(id, initialBalance, actor, Instant.now()));
        Account account = new Account(id, initialBalance);
        accounts.put(id, account);
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
        eventStore.append(new FundsCredited(accountId, amount, actor, Instant.now()));
        account.credit(amount);
        logger.info("Credited {} to account {}", amount, accountId);
        return account;
    }

    public Account debit(Actor actor, String accountId, BigDecimal amount) {
        Account account = getAccount(accountId);
        try {
            account.debit(amount);
        } catch (IllegalArgumentException e) {
            logger.warn("Insufficient funds for debit of {} from account {}", amount, accountId);
            throw new InsufficientFundsException(accountId);
        }
        try {
            eventStore.append(new FundsDebited(accountId, amount, actor, Instant.now()));
        } catch (RuntimeException e) {
            // O debito ja foi aplicado em memoria; desfaz para nao divergir do journal.
            account.credit(amount);
            throw e;
        }
        logger.info("Debited {} from account {}", amount, accountId);
        return account;
    }

    /** Reconstroi o estado a partir do journal, sem regravar eventos. */
    public void rebuild(List<DomainEvent> events) {
        accounts.clear();
        for (DomainEvent event : events) {
            if (event instanceof MoneyEvent moneyEvent) {
                apply(moneyEvent);
            }
        }
    }

    private void apply(MoneyEvent event) {
        switch (event) {
            case AccountOpened e -> accounts.put(e.accountId(), new Account(e.accountId(), e.initialBalance()));
            case AccountHolderAssigned e -> account(e.accountId()).assignHolder(e.holder());
            case FundsCredited e -> account(e.accountId()).credit(e.amount());
            case FundsDebited e -> account(e.accountId()).debit(e.amount());
            case TransferCompleted e -> {
                account(e.fromAccountId()).debit(e.amount());
                account(e.toAccountId()).credit(e.amount());
            }
        }
    }

    private Account account(String accountId) {
        Account account = accounts.get(accountId);
        if (account == null) {
            throw new AccountNotFoundException(accountId);
        }
        return account;
    }
}
