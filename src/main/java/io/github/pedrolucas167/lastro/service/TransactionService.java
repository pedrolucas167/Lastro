package io.github.pedrolucas167.lastro.service;

import io.github.pedrolucas167.lastro.domain.Actor;
import io.github.pedrolucas167.lastro.domain.Transaction;
import io.github.pedrolucas167.lastro.domain.TransactionStatus;
import io.github.pedrolucas167.lastro.domain.TransactionType;
import io.github.pedrolucas167.lastro.exception.AccountAccessDeniedException;
import io.github.pedrolucas167.lastro.exception.ClientLimitExceededException;
import io.github.pedrolucas167.lastro.exception.ClientSuspendedException;
import io.github.pedrolucas167.lastro.exception.TransactionFailedException;
import io.github.pedrolucas167.lastro.exception.TransferIdempotencyKeyReusedException;
import io.github.pedrolucas167.lastro.journal.DomainEvent;
import io.github.pedrolucas167.lastro.journal.JournalRebuildable;
import io.github.pedrolucas167.lastro.journal.TransferCompleted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TransactionService implements JournalRebuildable {
    private static final Logger logger = LoggerFactory.getLogger(TransactionService.class);
    private final AccountService accountService;
    private final ClientService clientService;
    private final Map<String, Transaction> transactions = new ConcurrentHashMap<>();
    private final Map<String, Transaction> transactionsByIdempotencyKey = new ConcurrentHashMap<>();

    public TransactionService(AccountService accountService, ClientService clientService) {
        this.accountService = accountService;
        this.clientService = clientService;
    }

    public Transaction transfer(Actor actor, String fromAccountId, String toAccountId, BigDecimal amount) {
        return transfer(actor, fromAccountId, toAccountId, amount, null);
    }

    /**
     * Transfere e reivindica a chave de idempotencia na mesma operacao: dois reenvios simultaneos
     * com a mesma chave movimentam o dinheiro uma vez so, e o segundo recebe a transferencia que
     * ja foi feita.
     */
    public Transaction transfer(Actor actor, String fromAccountId, String toAccountId, BigDecimal amount,
                                String idempotencyKey) {
        if (isBlank(idempotencyKey)) {
            return executeTransfer(actor, fromAccountId, toAccountId, amount, null);
        }

        Transaction transaction = transactionsByIdempotencyKey.computeIfAbsent(idempotencyKey,
            key -> executeTransfer(actor, fromAccountId, toAccountId, amount, key));

        if (transaction.getStatus() != TransactionStatus.COMPLETED) {
            // Tentativa que nao virou fato: o journal nao tem o que devolver num reenvio, entao
            // a chave nao fica presa a ela e o reenvio tenta de novo.
            transactionsByIdempotencyKey.remove(idempotencyKey, transaction);
            return transaction;
        }

        if (!sameCommand(transaction, fromAccountId, toAccountId, amount)) {
            throw new TransferIdempotencyKeyReusedException(transaction.getId());
        }

        return transaction;
    }

    private Transaction executeTransfer(Actor actor, String fromAccountId, String toAccountId, BigDecimal amount,
                                        String idempotencyKey) {
        String transactionId = UUID.randomUUID().toString();
        Transaction transaction = new Transaction(
            transactionId, fromAccountId, toAccountId, amount, TransactionType.TRANSFER
        );

        logger.info("Initiating transfer {} from {} to {} amount {}", 
            transactionId, fromAccountId, toAccountId, amount);

        try {
            // Cliente suspenso nao tira dinheiro da conta, e o limite diario soma o que ja saiu
            // hoje: as duas recusas acontecem antes de o dinheiro se mover.
            String holder = accountService.getAccount(fromAccountId).getHolder();
            clientService.ensureMayOperate(holder);
            clientService.ensureTransferWithinDailyLimit(holder,
                accountService.transferredOutOn(holder, LocalDate.now()), amount);
            // O dinheiro sai pelo mesmo caminho de todo movimento: a conta, o journal e o livro.
            boolean moved = accountService.transfer(new TransferCompleted(transactionId, fromAccountId,
                toAccountId, amount, idempotencyKey, actor, Instant.now()));
            if (moved) {
                transaction.setStatus(TransactionStatus.COMPLETED);
                logger.info("Transfer {} completed successfully", transactionId);
            } else {
                transaction.setStatus(TransactionStatus.FAILED);
                logger.warn("Transfer {} failed: insufficient funds", transactionId);
            }
        } catch (AccountAccessDeniedException | ClientSuspendedException
                 | ClientLimitExceededException e) {
            // Recusa de acesso ou de limite nao e falha da transferencia: e 403, nao 400.
            throw e;
        } catch (Exception e) {
            transaction.setStatus(TransactionStatus.FAILED);
            logger.error("Transfer {} failed with exception", transactionId, e);
            throw new TransactionFailedException(e.getMessage());
        }

        transactions.put(transactionId, transaction);
        return transaction;
    }

    /** Reconstroi as transacoes a partir do journal. A chave de idempotencia volta com elas. */
    public void rebuild(List<DomainEvent> events) {
        transactions.clear();
        transactionsByIdempotencyKey.clear();
        for (DomainEvent event : events) {
            if (event instanceof TransferCompleted e) {
                Transaction transaction = new Transaction(
                    e.transactionId(), e.fromAccountId(), e.toAccountId(), e.amount(), TransactionType.TRANSFER);
                transaction.setStatus(TransactionStatus.COMPLETED);
                transactions.put(e.transactionId(), transaction);
                if (!isBlank(e.idempotencyKey())) {
                    transactionsByIdempotencyKey.put(e.idempotencyKey(), transaction);
                }
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

    /** Chave repetida para outra transferencia e erro do cliente, nao reenvio. */
    private static boolean sameCommand(Transaction transaction, String fromAccountId, String toAccountId,
                                       BigDecimal amount) {
        return transaction.getFromAccountId().equals(fromAccountId)
            && transaction.getToAccountId().equals(toAccountId)
            && transaction.getAmount().compareTo(amount) == 0;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
