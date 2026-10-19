package io.github.pedrolucas167.lastro.service;

import io.github.pedrolucas167.lastro.domain.Actor;
import io.github.pedrolucas167.lastro.domain.Position;
import io.github.pedrolucas167.lastro.exception.RegulatoryException;
import io.github.pedrolucas167.lastro.journal.CustodyTransferred;
import io.github.pedrolucas167.lastro.journal.EventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Custodia: a acao entra no sistema e passa de uma conta para outra, sem passar pelo mercado.
 *
 * E por onde a acao existe -- o cliente que traz a carteira de outro custodiante, o back office que
 * registra a posicao inicial, o ajuste entre contas. Sem isso, uma conta nova nao teria o que
 * vender: toda venda precisa de acao que ja exista nela.
 *
 * A acao chega no destino com o preco medio de quem mandou: custodia move quantidade e custo, e o
 * destino faz a media com o que ja tinha.
 */
@Service
public class CustodyService {
    private static final Logger logger = LoggerFactory.getLogger(CustodyService.class);
    private static final BigDecimal MIN_PRICE = new BigDecimal("0.01");

    private final AccountService accountService;
    private final PositionService positionService;
    private final RegulatoryValidationService regulatoryValidationService;
    private final EventStore eventStore;

    public CustodyService(AccountService accountService, PositionService positionService,
                          RegulatoryValidationService regulatoryValidationService,
                          EventStore eventStore) {
        this.accountService = accountService;
        this.positionService = positionService;
        this.regulatoryValidationService = regulatoryValidationService;
        this.eventStore = eventStore;
    }

    /** Deposito: a acao entra no sistema, vinda de fora, no custo medio informado. */
    public CustodyTransfer deposit(Actor actor, String toAccountId, String symbol, int quantity,
                                   BigDecimal price) {
        validate(symbol, quantity);
        if (price == null || price.compareTo(MIN_PRICE) < 0) {
            throw new RegulatoryException("Custody deposit needs a positive average price");
        }
        requireAccount(toAccountId);

        CustodyTransfer transfer = move(actor, null, toAccountId, symbol, quantity, price);
        logger.info("Deposited {} {} at {} into account {}", quantity, symbol, price, toAccountId);
        return transfer;
    }

    /** Transferencia: a acao passa de uma conta para outra, no preco medio que ela ja tinha. */
    public CustodyTransfer transfer(Actor actor, String fromAccountId, String toAccountId,
                                    String symbol, int quantity) {
        validate(symbol, quantity);
        if (fromAccountId.equals(toAccountId)) {
            throw new RegulatoryException("Custody transfer needs two different accounts");
        }
        requireAccount(fromAccountId);
        requireAccount(toAccountId);

        Position origin = positionService.getPosition(fromAccountId, symbol);
        if (origin.getAvailable() < quantity) {
            throw new RegulatoryException(String.format(
                "Insufficient position in account %s for %s: transfer of %d, available %d",
                fromAccountId, symbol, quantity, origin.getAvailable()));
        }

        CustodyTransfer transfer = move(actor, fromAccountId, toAccountId, symbol, quantity,
            origin.getAveragePrice());
        logger.info("Transferred custody of {} {} from account {} to account {}",
            quantity, symbol, fromAccountId, toAccountId);
        return transfer;
    }

    /** O fato vai ao journal antes do movimento, e o movimento e o mesmo que o replay faz. */
    private CustodyTransfer move(Actor actor, String fromAccountId, String toAccountId, String symbol,
                                 int quantity, BigDecimal price) {
        eventStore.append(new CustodyTransferred(fromAccountId, toAccountId, symbol, quantity, price,
            actor, Instant.now()));
        positionService.applyCustody(fromAccountId, toAccountId, symbol, quantity, price);

        int fromQuantity = fromAccountId == null
            ? 0
            : positionService.getPosition(fromAccountId, symbol).getQuantity();
        return new CustodyTransfer(fromAccountId, fromQuantity, toAccountId,
            positionService.getPosition(toAccountId, symbol).getQuantity(), symbol, quantity, price);
    }

    private void validate(String symbol, int quantity) {
        regulatoryValidationService.validateSymbolFormat(symbol);
        if (quantity < 1) {
            throw new RegulatoryException("Custody quantity must be at least 1");
        }
    }

    private void requireAccount(String accountId) {
        accountService.getAccount(accountId);
    }
}
