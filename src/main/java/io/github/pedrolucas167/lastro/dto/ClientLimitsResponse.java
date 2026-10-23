package io.github.pedrolucas167.lastro.dto;

import io.github.pedrolucas167.lastro.domain.ClientLimits;

import java.math.BigDecimal;

/** Os limites do cliente. Campo nulo e "sem limite". */
public record ClientLimitsResponse(BigDecimal orderNotional, BigDecimal dailyTransfer) {

    public static ClientLimitsResponse of(ClientLimits limits) {
        return new ClientLimitsResponse(limits.orderNotional(), limits.dailyTransfer());
    }
}
