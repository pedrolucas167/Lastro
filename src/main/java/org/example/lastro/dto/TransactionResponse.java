package org.example.lastro.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@AllArgsConstructor
public class TransactionResponse {
    private String id;
    private String fromAccountId;
    private String toAccountId;
    private BigDecimal amount;
    private String type;
    private String status;
    private Instant timestamp;
}
