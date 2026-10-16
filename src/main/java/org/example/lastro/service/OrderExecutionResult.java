package org.example.lastro.service;

import org.example.lastro.domain.Order;
import org.example.lastro.domain.Trade;

import java.util.List;

public record OrderExecutionResult(Order order, List<Trade> trades) {
}
