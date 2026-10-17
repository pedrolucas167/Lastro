package io.github.pedrolucas167.lastro.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class PositionResponse {
    private String accountId;
    private String symbol;
    private Integer quantity;
    private BigDecimal averagePrice;
}
