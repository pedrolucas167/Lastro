package org.example.lastro.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class AccountResponse {
    private String id;
    private BigDecimal balance;
}
