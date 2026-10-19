package io.github.pedrolucas167.lastro.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CustodyTransferRequest {
    @NotBlank(message = "Source account is required")
    private String fromAccountId;

    @NotBlank(message = "Destination account is required")
    private String toAccountId;

    @NotBlank(message = "Symbol is required")
    private String symbol;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;
}
