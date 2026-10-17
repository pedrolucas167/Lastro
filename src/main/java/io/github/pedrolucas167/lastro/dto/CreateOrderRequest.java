package io.github.pedrolucas167.lastro.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateOrderRequest {
    @NotBlank(message = "Symbol is required")
    private String symbol;

    @NotNull(message = "Side is required")
    private String side;

    @NotNull(message = "Type is required")
    private String type;

    private String timeInForce;

    private BigDecimal price;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    @NotBlank(message = "Account ID is required")
    private String accountId;
}
