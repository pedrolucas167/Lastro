package io.github.pedrolucas167.lastro.service;

import java.math.BigDecimal;

/** O recibo da custodia: o que mudou nas duas pontas. A origem e nula no deposito. */
public record CustodyTransfer(String fromAccountId, int fromQuantity, String toAccountId,
                              int toQuantity, String symbol, int quantity, BigDecimal price) {
}
