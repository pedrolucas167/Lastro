package io.github.pedrolucas167.lastro.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MarketStatusResponse {
    private String symbol;
    private boolean halted;
    private String reason;
}
