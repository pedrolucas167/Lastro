package org.example.lastro.service;

import org.example.lastro.domain.Account;
import org.example.lastro.domain.Transaction;
import org.example.lastro.domain.TransactionStatus;
import org.example.lastro.domain.TransactionType;
import org.example.lastro.exception.TransactionFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TransactionService {
    private static final Logger logger = LoggerFactory.getLogger(TransactionService.class);
    private final AccountService accountService;
    private final IdempotencyService idempotencyService;
    private final Map<String, Transaction> transactions = new ConcurrentHashMap<>();

    public TransactionService(AccountService accountService, IdempotencyService idempotencyService) {
        this.accountService = accountService;
        this.idempotencyService = idempotencyService;
    }

    public Transaction transfer(String fromAccountId, String toAccountId, BigDecimal amount) {
        return transfer(fromAccountId, toAccountId, amount, null);
    }

    public Transaction transfer(String fromAccountId, String toAccountId, BigDecimal amount, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return idempotencyService.getOrRecord(idempotencyKey, Instant.now(), 
                () -> executeTransfer(fromAccountId, toAccountId, amount));
        }
        return executeTransfer(fromAccountId, toAccountId, amount);
    }

    private Transaction executeTransfer(String fromAccountId, String toAccountId, BigDecimal amount) {
        String transactionId = UUID.randomUUID().toString();
        Transaction transaction = new Transaction(
            transactionId, fromAccountId, toAccountId, amount, TransactionType.TRANSFER
        );

        logger.info("Initiating transfer {} from {} to {} amount {}", 
            transactionId, fromAccountId, toAccountId, amount);

        try {
            Account fromAccount = accountService.getAccount(fromAccountId);
            Account toAccount = accountService.getAccount(toAccountId);

            if (fromAccount.tryDebit(amount)) {
                toAccount.credit(amount);
                transaction.setStatus(TransactionStatus.COMPLETED);
                logger.info("Transfer {} completed successfully", transactionId);
            } else {
                transaction.setStatus(TransactionStatus.FAILED);
                logger.warn("Transfer {} failed: insufficient funds", transactionId);
            }
        } catch (Exception e) {
            transaction.setStatus(TransactionStatus.FAILED);
            logger.error("Transfer {} failed with exception", transactionId, e);
            throw new TransactionFailedException(e.getMessage());
        }

        transactions.put(transactionId, transaction);
        return transaction;
    }

    public Transaction getTransaction(String transactionId) {
        Transaction transaction = transactions.get(transactionId);
        if (transaction == null) {
            logger.warn("Transaction not found: {}", transactionId);
        }
        return transaction;
    }
}
