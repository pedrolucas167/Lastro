package io.github.pedrolucas167.lastro.service;

import io.github.pedrolucas167.lastro.domain.Order;
import io.github.pedrolucas167.lastro.domain.Trade;

import java.util.List;

public record OrderExecutionResult(Order order, List<Trade> trades) {
}
