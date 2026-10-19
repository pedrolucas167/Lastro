package io.github.pedrolucas167.lastro.api;

import jakarta.validation.Valid;
import io.github.pedrolucas167.lastro.config.Roles;
import io.github.pedrolucas167.lastro.domain.Transaction;
import io.github.pedrolucas167.lastro.dto.TransactionRequest;
import io.github.pedrolucas167.lastro.dto.TransactionResponse;
import io.github.pedrolucas167.lastro.service.TransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {
    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * `Idempotency-Key` e a chave do cliente para reenvio: repetir a chamada com a mesma chave
     * devolve a transferencia que ja foi feita, sem movimentar o dinheiro de novo.
     */
    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(Authentication authentication,
                                                       @RequestHeader(value = "Idempotency-Key", required = false)
                                                       String idempotencyKey,
                                                       @Valid @RequestBody TransactionRequest request) {
        Transaction transaction = transactionService.transfer(
            Roles.actor(authentication),
            request.getFromAccountId(),
            request.getToAccountId(),
            request.getAmount(),
            idempotencyKey
        );
        return ResponseEntity.ok(toResponse(transaction));
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> getTransaction(Authentication authentication,
                                                             @PathVariable String transactionId) {
        Transaction transaction = transactionService.getTransaction(Roles.actor(authentication), transactionId);
        if (transaction == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toResponse(transaction));
    }

    private TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
            transaction.getId(),
            transaction.getFromAccountId(),
            transaction.getToAccountId(),
            transaction.getAmount(),
            transaction.getType().name(),
            transaction.getStatus().name(),
            transaction.getTimestamp()
        );
    }
}
