package org.example.lastro.dto;

public record MarketStatusResponse(String symbol, boolean halted, String reason) {
}
