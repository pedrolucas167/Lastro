package io.github.pedrolucas167.lastro.api;

import io.github.pedrolucas167.lastro.dto.TrialBalanceResponse;
import io.github.pedrolucas167.lastro.ledger.LedgerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ledger")
public class LedgerController {
    private final LedgerService ledger;

    public LedgerController(LedgerService ledger) {
        this.ledger = ledger;
    }

    /**
     * O livro inteiro, conta a conta. E ato de operador: a matriz de rotas ja exige o papel, e o
     * balanco mostra o caixa na liquidante e o que ela deve aos clientes lado a lado.
     */
    @GetMapping("/trial-balance")
    public ResponseEntity<TrialBalanceResponse> trialBalance() {
        return ResponseEntity.ok(TrialBalanceResponse.of(ledger));
    }
}
