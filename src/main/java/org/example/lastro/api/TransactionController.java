package org.example.lastro.api;

import jakarta.validation.Valid;
import org.example.lastro.domain.Transaction;
import org.example.lastro.dto.TransactionRequest;
import org.example.lastro.dto.TransactionResponse;
import org.example.lastro.service.TransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {
    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(@Valid @RequestBody TransactionRequest request) {
        Transaction transaction = transactionService.transfer(
            request.getFromAccountId(),
            request.getToAccountId(),
            request.getAmount(),
            request.getIdempotencyKey()
        );
        return ResponseEntity.ok(new TransactionResponse(
            transaction.getId(),
            transaction.getFromAccountId(),
            transaction.getToAccountId(),
            transaction.getAmount(),
            transaction.getType().name(),
            transaction.getStatus().name(),
            transaction.getTimestamp()
        ));
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> getTransaction(@PathVariable String transactionId) {
        Transaction transaction = transactionService.getTransaction(transactionId);
        if (transaction == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new TransactionResponse(
            transaction.getId(),
            transaction.getFromAccountId(),
            transaction.getToAccountId(),
            transaction.getAmount(),
            transaction.getType().name(),
            transaction.getStatus().name(),
            transaction.getTimestamp()
        ));
    }
}
