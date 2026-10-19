package io.github.pedrolucas167.lastro.dto;

import io.github.pedrolucas167.lastro.service.CustodyTransfer;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/** O que a custodia fez: a quantidade nas duas pontas. A origem vem nula no deposito. */
@Data
@AllArgsConstructor
public class CustodyTransferResponse {
    private String fromAccountId;
    private Integer fromQuantity;
    private String toAccountId;
    private Integer toQuantity;
    private String symbol;
    private Integer quantity;
    private BigDecimal price;

    public static CustodyTransferResponse of(CustodyTransfer transfer) {
        return new CustodyTransferResponse(transfer.fromAccountId(), transfer.fromQuantity(),
            transfer.toAccountId(), transfer.toQuantity(), transfer.symbol(), transfer.quantity(),
            transfer.price());
    }
}
