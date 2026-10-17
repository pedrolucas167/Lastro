package org.example.lastro.service;

import org.example.lastro.domain.Account;
import org.example.lastro.domain.Transaction;
import org.example.lastro.domain.TransactionStatus;
import org.example.lastro.domain.TransactionType;
import org.example.lastro.exception.TransactionFailedException;
import org.example.lastro.journal.DomainEvent;
import org.example.lastro.journal.EventStore;
import org.example.lastro.journal.JournalRebuildable;
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
public class TransactionService implements JournalRebuildable {
    private static final Logger logger = LoggerFactory.getLogger(TransactionService.class);
    private final AccountService accountService;
    private final IdempotencyService idempotencyService;
    private final EventStore eventStore;
    private final Map<String, Transaction> transactions = new ConcurrentHashMap<>();

    public TransactionService(AccountService accountService, IdempotencyService idempotencyService,
                              EventStore eventStore) {
        this.accountService = accountService;
        this.idempotencyService = idempotencyService;
        this.eventStore = eventStore;
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
                journalTransfer(transactionId, fromAccountId, toAccountId, amount, fromAccount, toAccount);
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

    private void journalTransfer(String transactionId, String fromAccountId, String toAccountId,
                                 BigDecimal amount, Account fromAccount, Account toAccount) {
        try {
            eventStore.append(new TransferCompleted(
                transactionId, fromAccountId, toAccountId, amount, Instant.now()));
        } catch (RuntimeException e) {
            // A transferencia ja foi aplicada em memoria; desfaz para nao divergir do journal.
            toAccount.debit(amount);
            fromAccount.credit(amount);
            throw e;
        }
    }

    /** Reconstroi as transacoes a partir do journal. */
    public void rebuild(List<DomainEvent> events) {
        transactions.clear();
        for (DomainEvent event : events) {
            if (event instanceof TransferCompleted e) {
                Transaction transaction = new Transaction(
                    e.transactionId(), e.fromAccountId(), e.toAccountId(), e.amount(), TransactionType.TRANSFER);
                transaction.setStatus(TransactionStatus.COMPLETED);
                transactions.put(e.transactionId(), transaction);
            }
        }
    }

    public Transaction getTransaction(String transactionId) {
        Transaction transaction = transactions.get(transactionId);
        if (transaction == null) {
            logger.warn("Transaction not found: {}", transactionId);
        }
        return transaction;
    }
}
