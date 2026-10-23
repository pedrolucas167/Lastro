package io.github.pedrolucas167.lastro.dto;

import java.math.BigDecimal;

/** Os limites a definir. Campo nulo remove aquele limite. */
public record ClientLimitsRequest(BigDecimal orderNotional, BigDecimal dailyTransfer) {
}
