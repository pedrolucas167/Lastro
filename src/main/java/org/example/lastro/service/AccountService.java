package org.example.lastro.service;

import org.example.lastro.domain.Account;
import org.example.lastro.exception.AccountNotFoundException;
import org.example.lastro.exception.InsufficientFundsException;
import org.example.lastro.journal.AccountOpened;
import org.example.lastro.journal.DomainEvent;
import org.example.lastro.journal.EventStore;
import org.example.lastro.journal.FundsCredited;
import org.example.lastro.journal.FundsDebited;
import org.example.lastro.journal.TransferCompleted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AccountService {
    private static final Logger logger = LoggerFactory.getLogger(AccountService.class);
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final EventStore eventStore;

    public AccountService(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    public Account createAccount(BigDecimal initialBalance) {
        String id = UUID.randomUUID().toString();
        eventStore.append(new AccountOpened(id, initialBalance, Instant.now()));
        Account account = new Account(id, initialBalance);
        accounts.put(id, account);
        logger.info("Created account {} with initial balance {}", id, initialBalance);
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

    public Account credit(String accountId, BigDecimal amount) {
        Account account = getAccount(accountId);
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        eventStore.append(new FundsCredited(accountId, amount, Instant.now()));
        account.credit(amount);
        logger.info("Credited {} to account {}", amount, accountId);
        return account;
    }

    public Account debit(String accountId, BigDecimal amount) {
        Account account = getAccount(accountId);
        try {
            account.debit(amount);
        } catch (IllegalArgumentException e) {
            logger.warn("Insufficient funds for debit of {} from account {}", amount, accountId);
            throw new InsufficientFundsException(accountId);
        }
        try {
            eventStore.append(new FundsDebited(accountId, amount, Instant.now()));
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
            apply(event);
        }
    }

    private void apply(DomainEvent event) {
        switch (event) {
            case AccountOpened e -> accounts.put(e.accountId(), new Account(e.accountId(), e.initialBalance()));
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
