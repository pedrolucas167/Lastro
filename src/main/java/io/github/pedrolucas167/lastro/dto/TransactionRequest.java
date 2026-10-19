package io.github.pedrolucas167.lastro.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransactionRequest {
    @NotBlank(message = "From account ID is required")
    private String fromAccountId;

    @NotBlank(message = "To account ID is required")
    private String toAccountId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    /**
     * Campo antigo: a chave passou para o cabecalho `Idempotency-Key`. Aceitar em silencio aqui
     * deixaria o cliente sem garantia nenhuma achando que tem.
     */
    @Null(message = "Send the idempotency key in the Idempotency-Key header")
    private String idempotencyKey;
}
