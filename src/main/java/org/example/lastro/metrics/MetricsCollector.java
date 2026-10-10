package org.example.lastro.metrics;

import org.example.lastro.domain.Trade;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class MetricsCollector {
    private final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
    private final List<MetricSnapshot> snapshots = new ArrayList<>();
    private final List<Trade> trades = new CopyOnWriteArrayList<>();

    public MetricSnapshot collectSnapshot() {
        double cpuLoad = osBean.getSystemLoadAverage();
        int availableProcessors = osBean.getAvailableProcessors();
        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();
        long usedMemory = totalMemory - freeMemory;
        double memoryUsagePercent = (usedMemory * 100.0) / totalMemory;

        MetricSnapshot snapshot = new MetricSnapshot(
            System.currentTimeMillis(),
            cpuLoad,
            availableProcessors,
            usedMemory,
            totalMemory,
            memoryUsagePercent
        );
        
        snapshots.add(snapshot);
        return snapshot;
    }

    public void recordTrade(Trade trade) {
        trades.add(trade);
    }

    public List<MetricSnapshot> getSnapshots() {
        return new ArrayList<>(snapshots);
    }

    public List<Trade> getTrades() {
        return new ArrayList<>(trades);
    }

    public void clearSnapshots() {
        snapshots.clear();
    }

    public void clearTrades() {
        trades.clear();
    }

    public void clear() {
        snapshots.clear();
        trades.clear();
    }

    public MetricSummary getSummary() {
        if (snapshots.isEmpty()) {
            return new MetricSummary(0, 0, 0, 0, 0, 0);
        }

        double avgCpu = snapshots.stream()
            .mapToDouble(MetricSnapshot::getCpuLoad)
            .average()
            .orElse(0);

        double maxCpu = snapshots.stream()
            .mapToDouble(MetricSnapshot::getCpuLoad)
            .max()
            .orElse(0);

        double avgMemory = snapshots.stream()
            .mapToDouble(MetricSnapshot::getMemoryUsagePercent)
            .average()
            .orElse(0);

        double maxMemory = snapshots.stream()
            .mapToDouble(MetricSnapshot::getMemoryUsagePercent)
            .max()
            .orElse(0);

        return new MetricSummary(
            snapshots.size(),
            avgCpu,
            maxCpu,
            avgMemory,
            maxMemory,
            snapshots.get(snapshots.size() - 1).getTimestamp() - snapshots.get(0).getTimestamp()
        );
    }

    public TradingMetrics getTradingMetrics() {
        if (trades.isEmpty()) {
            return new TradingMetrics(0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0);
        }

        int totalVolume = trades.stream()
            .mapToInt(Trade::getQuantity)
            .sum();

        BigDecimal totalValue = trades.stream()
            .map(t -> t.getPrice().multiply(new BigDecimal(t.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal vwap = totalValue.divide(new BigDecimal(totalVolume), 2, RoundingMode.HALF_UP);

        BigDecimal avgPrice = trades.stream()
            .map(Trade::getPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(new BigDecimal(trades.size()), 2, RoundingMode.HALF_UP);

        return new TradingMetrics(
            trades.size(),
            totalVolume,
            vwap,
            avgPrice,
            trades.size()
        );
    }
}
