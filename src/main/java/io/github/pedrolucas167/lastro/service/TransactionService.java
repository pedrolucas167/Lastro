package io.github.pedrolucas167.lastro.service;

import io.github.pedrolucas167.lastro.domain.Account;
import io.github.pedrolucas167.lastro.domain.Actor;
import io.github.pedrolucas167.lastro.domain.Transaction;
import io.github.pedrolucas167.lastro.domain.TransactionStatus;
import io.github.pedrolucas167.lastro.domain.TransactionType;
import io.github.pedrolucas167.lastro.exception.AccountAccessDeniedException;
import io.github.pedrolucas167.lastro.exception.TransactionFailedException;
import io.github.pedrolucas167.lastro.journal.DomainEvent;
import io.github.pedrolucas167.lastro.journal.EventStore;
import io.github.pedrolucas167.lastro.journal.JournalRebuildable;
import io.github.pedrolucas167.lastro.journal.TransferCompleted;
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

    public Transaction transfer(Actor actor, String fromAccountId, String toAccountId, BigDecimal amount) {
        return transfer(actor, fromAccountId, toAccountId, amount, null);
    }

    public Transaction transfer(Actor actor, String fromAccountId, String toAccountId, BigDecimal amount,
                                String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return idempotencyService.getOrRecord(idempotencyKey, Instant.now(), 
                () -> executeTransfer(actor, fromAccountId, toAccountId, amount));
        }
        return executeTransfer(actor, fromAccountId, toAccountId, amount);
    }

    private Transaction executeTransfer(Actor actor, String fromAccountId, String toAccountId, BigDecimal amount) {
        String transactionId = UUID.randomUUID().toString();
        Transaction transaction = new Transaction(
            transactionId, fromAccountId, toAccountId, amount, TransactionType.TRANSFER
        );

        logger.info("Initiating transfer {} from {} to {} amount {}", 
            transactionId, fromAccountId, toAccountId, amount);

        try {
            // So sai dinheiro de conta que o ator tem; o destino e escolha de quem paga.
            Account fromAccount = accountService.getAccountFor(actor, fromAccountId);
            Account toAccount = accountService.getAccount(toAccountId);

            if (fromAccount.tryDebit(amount)) {
                toAccount.credit(amount);
                journalTransfer(transactionId, fromAccountId, toAccountId, amount, actor, fromAccount, toAccount);
                transaction.setStatus(TransactionStatus.COMPLETED);
                logger.info("Transfer {} completed successfully", transactionId);
            } else {
                transaction.setStatus(TransactionStatus.FAILED);
                logger.warn("Transfer {} failed: insufficient funds", transactionId);
            }
        } catch (AccountAccessDeniedException e) {
            // Autorizacao nao e falha da transferencia: negar acesso e 403, nao 400.
            throw e;
        } catch (Exception e) {
            transaction.setStatus(TransactionStatus.FAILED);
            logger.error("Transfer {} failed with exception", transactionId, e);
            throw new TransactionFailedException(e.getMessage());
        }

        transactions.put(transactionId, transaction);
        return transaction;
    }

    private void journalTransfer(String transactionId, String fromAccountId, String toAccountId,
                                 BigDecimal amount, Actor actor, Account fromAccount, Account toAccount) {
        try {
            eventStore.append(new TransferCompleted(
                transactionId, fromAccountId, toAccountId, amount, actor, Instant.now()));
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

    public Transaction getTransaction(Actor actor, String transactionId) {
        Transaction transaction = transactions.get(transactionId);
        if (transaction == null) {
            logger.warn("Transaction not found: {}", transactionId);
            return null;
        }
        if (!actor.privileged() && !touches(actor, transaction)) {
            // A transacao existe, mas e de outros: nem confirma a existencia nem entrega o extrato.
            throw new AccountAccessDeniedException(
                "Transaction " + transactionId + " does not involve an account you hold");
        }
        return transaction;
    }

    private boolean touches(Actor actor, Transaction transaction) {
        return accountService.canAccess(actor, transaction.getFromAccountId())
            || accountService.canAccess(actor, transaction.getToAccountId());
    }
}
