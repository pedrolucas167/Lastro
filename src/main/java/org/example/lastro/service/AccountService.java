package org.example.lastro.service;

import org.example.lastro.domain.Account;
import org.example.lastro.exception.AccountNotFoundException;
import org.example.lastro.exception.InsufficientFundsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AccountService {
    private static final Logger logger = LoggerFactory.getLogger(AccountService.class);
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    public Account createAccount(BigDecimal initialBalance) {
        String id = UUID.randomUUID().toString();
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
        account.credit(amount);
        logger.info("Credited {} to account {}", amount, accountId);
        return account;
    }

    public Account debit(String accountId, BigDecimal amount) {
        Account account = getAccount(accountId);
        try {
            account.debit(amount);
            logger.info("Debited {} from account {}", amount, accountId);
            return account;
        } catch (IllegalArgumentException e) {
            logger.warn("Insufficient funds for debit of {} from account {}", amount, accountId);
            throw new InsufficientFundsException(accountId);
        }
    }
}
