package io.github.pedrolucas167.lastro.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@AllArgsConstructor
public class TradeResponse {
    private String id;
    private String symbol;
    private String buyOrderId;
    private String sellOrderId;
    private BigDecimal price;
    private Integer quantity;
    private Instant timestamp;
}
