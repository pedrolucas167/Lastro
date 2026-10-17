package org.example.lastro.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@AllArgsConstructor
public class OrderResponse {
    private String id;
    private String symbol;
    private String side;
    private String type;
    private String timeInForce;
    private BigDecimal price;
    private Integer originalQuantity;
    private Integer remainingQuantity;
    private String status;
    private Instant createdAt;
}
