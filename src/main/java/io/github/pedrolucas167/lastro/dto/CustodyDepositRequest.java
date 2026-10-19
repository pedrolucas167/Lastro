package io.github.pedrolucas167.lastro.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CustodyDepositRequest {
    @NotBlank(message = "Account ID is required")
    private String accountId;

    @NotBlank(message = "Symbol is required")
    private String symbol;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    /** Custo medio com que a acao entra: e o que o destino passa a ter. */
    @NotNull(message = "Average price is required")
    @DecimalMin(value = "0.01", message = "Average price must be at least 0.01")
    private BigDecimal price;
}
