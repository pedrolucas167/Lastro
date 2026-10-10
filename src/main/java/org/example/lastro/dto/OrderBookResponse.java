package org.example.lastro.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class OrderBookResponse {
    private String symbol;
    private BigDecimal bestBid;
    private BigDecimal bestAsk;
    private BigDecimal spread;
    private Integer totalBidQuantity;
    private Integer totalAskQuantity;
}
