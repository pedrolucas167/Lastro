package io.github.pedrolucas167.lastro.service;

import io.github.pedrolucas167.lastro.config.PriceBandProperties;
import io.github.pedrolucas167.lastro.domain.Order;
import io.github.pedrolucas167.lastro.domain.OrderSide;
import io.github.pedrolucas167.lastro.domain.OrderType;
import io.github.pedrolucas167.lastro.exception.RegulatoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Pre-trade: nenhuma ordem chega ao engine sem lastro.
 *
 * Compra bloqueia o caixa que ela ainda pode gastar (preco-limite x quantidade restante); venda
 * bloqueia a acao. O bloqueio dura enquanto a ordem pode executar: o que executou virou pagamento
 * (o trade liquida) e o que nao vai mais executar volta ao disponivel. Checar e bloquear e o mesmo
 * passo atomico em Account e Position, entao duas ordens que chegam juntas nao prometem o mesmo
 * dinheiro nem a mesma acao.
 *
 * O bloqueio nao e evento do journal: e consequencia das ordens em aberto, e o replay o reconstroi
 * somando-as -- como faz com a posicao.
 *
 * Ordem a mercado nao tem preco para bloquear. Como a banda de preco ja limita ate onde a execucao
 * pode ir, o teto da banda e o valor bloqueado; simbolo sem banda de referencia nao aceita compra
 * a mercado, porque nao ha teto que garanta o caixa. Venda a mercado nao tem esse problema: o
 * lastro dela e a acao, que tem quantidade conhecida.
 */
@Service
public class PreTradeRiskService {

    private static final Logger logger = LoggerFactory.getLogger(PreTradeRiskService.class);

    private final AccountService accountService;
    private final PositionService positionService;
    private final PriceBandProperties priceBand;

    public PreTradeRiskService(AccountService accountService, PositionService positionService,
                               PriceBandProperties priceBand) {
        this.accountService = accountService;
        this.positionService = positionService;
        this.priceBand = priceBand;
    }

    /** O que o amend move: o lastro de antes, o que ele passaria a pedir e o que foi bloqueado a mais. */
    public record AmendHold(BigDecimal before, BigDecimal wanted, BigDecimal reserved) {
    }

    /**
     * Bloqueia o lastro da ordem nova e devolve o valor bloqueado. Recusar aqui e recusar antes de
     * a ordem existir: ordem sem lastro nao vira fato no journal.
     */
    public BigDecimal reserveForNewOrder(String symbol, OrderSide side, OrderType type,
                                         BigDecimal price, int quantity, String accountId) {
        if (side == OrderSide.SELL) {
            holdShares(accountId, symbol, BigDecimal.valueOf(quantity));
            return BigDecimal.valueOf(quantity);
        }
        BigDecimal amount = unitPrice(symbol, type, price).multiply(BigDecimal.valueOf(quantity));
        holdCash(accountId, amount);
        return amount;
    }

    /** A ordem nao chegou a existir: devolve o lastro reservado para ela. */
    public void releaseReserved(String accountId, String symbol, OrderSide side, BigDecimal reserved) {
        if (reserved.signum() == 0) {
            return;
        }
        if (side == OrderSide.BUY) {
            accountService.release(accountId, reserved);
        } else {
            positionService.release(accountId, symbol, reserved.intValueExact());
        }
    }

    /** A parte executada deixou de ser bloqueio e virou pagamento (liquidado pelo trade). */
    public void releaseFilled(Order order, int filledQuantity) {
        release(order, commitmentOf(order, order.getPrice(), filledQuantity));
    }

    /** A ordem nao vai mais executar o que sobrou -- cancelada, ou nao repousou (IOC/FOK/mercado). */
    public void releaseUnfilled(Order order) {
        if (order.isOpen()) {
            return;
        }
        release(order, commitmentOf(order, order.getPrice(), order.getRemainingQuantity()));
    }

    /** Bloqueia o que o amend passa a pedir a mais. Recusa antes do engine quando a conta nao cobre. */
    public AmendHold reserveForAmend(Order order, BigDecimal price, int quantity) {
        BigDecimal before = commitmentOf(order, order.getPrice(), order.getRemainingQuantity());
        BigDecimal wanted = commitmentOf(order, price, order.remainingAfterAmend(quantity));
        BigDecimal reserved = wanted.subtract(before).max(BigDecimal.ZERO);
        if (reserved.signum() > 0) {
            hold(order, reserved);
        }
        return new AmendHold(before, wanted, reserved);
    }

    /** O engine recusou o amend: o que foi bloqueado a mais volta. */
    public void rollbackAmend(Order order, AmendHold hold) {
        if (hold.reserved().signum() > 0) {
            release(order, hold.reserved());
        }
    }

    /**
     * O amend valeu: o lastro passa a ser o que a ordem garante de novo. O que executou no proprio
     * amend ja foi liberado pelos trades, entao aqui volta so o que o amend deixou de pedir.
     */
    public void finishAmend(Order order, AmendHold hold) {
        BigDecimal surplus = hold.before().subtract(hold.wanted());
        if (surplus.signum() > 0) {
            release(order, surplus);
        }
    }

    /**
     * Restaura o bloqueio no replay: para cada ordem ainda em aberto, o lastro e o que ela ainda
     * pode executar. A soma por conta (e por conta/simbolo, na venda) e o estado reconstruido -- a
     * conta nao aceita ordem nova ate cobri-lo.
     *
     * Um journal anterior ao lastro pode ter ordem de conta que nunca foi aberta (a API aceitava
     * qualquer id): nao ha onde bloquear, e a ordem fica sem lastro reconstruido -- com aviso, para
     * o operador saber qual e.
     */
    public void rebuildHolds(Collection<Order> orders) {
        Map<String, BigDecimal> cashByAccount = new HashMap<>();
        Map<PositionKey, Integer> sharesByPosition = new HashMap<>();

        for (Order order : orders) {
            if (!order.isOpen()) {
                continue;
            }
            if (!accountService.hasAccount(order.getAccountId())) {
                logger.warn("Order {} belongs to account {}, which the journal never opened: "
                    + "no hold to rebuild", order.getId(), order.getAccountId());
                continue;
            }
            if (order.getSide() == OrderSide.BUY) {
                BigDecimal commitment = commitmentOf(order, order.getPrice(),
                    order.getRemainingQuantity());
                cashByAccount.merge(order.getAccountId(), commitment, BigDecimal::add);
            } else {
                sharesByPosition.merge(new PositionKey(order.getAccountId(), order.getSymbol()),
                    order.getRemainingQuantity(), Integer::sum);
            }
        }

        cashByAccount.forEach(accountService::rebuildHeld);
        sharesByPosition.forEach((position, quantity) ->
            positionService.rebuildHeld(position.accountId(), position.symbol(), quantity));
    }

    /** O lastro que a ordem garante: caixa a gastar (compra) ou acoes a vender (venda). */
    private BigDecimal commitmentOf(Order order, BigDecimal price, int quantity) {
        if (order.getSide() != OrderSide.BUY) {
            return BigDecimal.valueOf(quantity);
        }
        return unitPrice(order.getSymbol(), order.getType(), price)
            .multiply(BigDecimal.valueOf(quantity));
    }

    private void hold(Order order, BigDecimal commitment) {
        if (order.getSide() == OrderSide.BUY) {
            holdCash(order.getAccountId(), commitment);
        } else {
            holdShares(order.getAccountId(), order.getSymbol(), commitment);
        }
    }

    private void release(Order order, BigDecimal commitment) {
        if (commitment.signum() == 0) {
            return;
        }
        if (order.getSide() == OrderSide.BUY) {
            accountService.release(order.getAccountId(), commitment);
        } else {
            positionService.release(order.getAccountId(), order.getSymbol(), commitment.intValueExact());
        }
    }

    private void holdCash(String accountId, BigDecimal amount) {
        if (!accountService.hold(accountId, amount)) {
            throw new RegulatoryException(String.format(
                "Insufficient buying power for account %s: order needs %s, available %s",
                accountId, amount, accountService.getAvailable(accountId)));
        }
    }

    private void holdShares(String accountId, String symbol, BigDecimal quantity) {
        int shares = quantity.intValueExact();
        if (!positionService.hold(accountId, symbol, shares)) {
            throw new RegulatoryException(String.format(
                "Insufficient position for account %s in %s: sell of %d, available %d",
                accountId, symbol, shares, positionService.getAvailable(accountId, symbol)));
        }
    }

    /** Preco unitario do lastro: o da ordem limitada, ou o teto da banda quando a ordem e a mercado. */
    private BigDecimal unitPrice(String symbol, OrderType type, BigDecimal price) {
        if (type == OrderType.LIMIT) {
            return price;
        }
        return priceBand.upperBound(symbol).orElseThrow(() -> new RegulatoryException(String.format(
            "Market buy of %s has no price ceiling: configure a band reference price for the "
                + "symbol or send a limit order", symbol)));
    }

    private record PositionKey(String accountId, String symbol) {
    }
}
