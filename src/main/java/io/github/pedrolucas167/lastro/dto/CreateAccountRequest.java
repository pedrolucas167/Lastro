package io.github.pedrolucas167.lastro.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateAccountRequest {
    @NotNull(message = "Initial balance is required")
    @DecimalMin(value = "0.00", message = "Initial balance must be non-negative")
    private BigDecimal initialBalance;

    /**
     * Titular da conta (o `sub` do Keycloak). So um privilegiado abre em nome de outro --
     * o titular que mandar este campo so pode mandar a propria identidade.
     */
    private String holder;
}
