package io.github.pedrolucas167.lastro.domain;

import java.math.BigDecimal;

/**
 * Os limites do cliente. Nulo e "sem limite" -- o limite existe quando o operador o define, e cada
 * definicao vira um fato no journal: o replay fica com a ultima.
 *
 * O teto por ordem vale para compras, porque e caixa que a ordem pode comprometer; venda nao
 * compromete caixa (nao ha descoberto) e nao passa por ele. O teto diario vale para o que o cliente
 * tira do sistema por transferencia, somado no dia do sistema.
 */
public record ClientLimits(BigDecimal orderNotional, BigDecimal dailyTransfer) {

    public static final ClientLimits NONE = new ClientLimits(null, null);

    public ClientLimits {
        requireNonNegative(orderNotional, "orderNotional");
        requireNonNegative(dailyTransfer, "dailyTransfer");
    }

    private static void requireNonNegative(BigDecimal value, String name) {
        if (value != null && value.signum() < 0) {
            throw new IllegalArgumentException(name + " cannot be negative");
        }
    }
}
