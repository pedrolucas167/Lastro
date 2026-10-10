package org.example.lastro.loadgenerator;

import java.util.List;

public class LoadTestResult {
    private final int totalRequests;
    private final long successCount;
    private final long failureCount;
    private final long totalDurationMs;
    private final List<Long> latencies;

    public LoadTestResult(int totalRequests, long successCount, long failureCount, 
                         long totalDurationMs, List<Long> latencies) {
        this.totalRequests = totalRequests;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.totalDurationMs = totalDurationMs;
        this.latencies = latencies;
    }

    public int getTotalRequests() {
        return totalRequests;
    }

    public long getSuccessCount() {
        return successCount;
    }

    public long getFailureCount() {
        return failureCount;
    }

    public long getTotalDurationMs() {
        return totalDurationMs;
    }

    public List<Long> getLatencies() {
        return latencies;
    }

    public double getThroughput() {
        return (successCount * 1000.0) / totalDurationMs;
    }

    public double getAverageLatency() {
        if (latencies.isEmpty()) return 0;
        return latencies.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    public double getP50Latency() {
        return getPercentile(50);
    }

    public double getP95Latency() {
        return getPercentile(95);
    }

    public double getP99Latency() {
        return getPercentile(99);
    }

    private double getPercentile(int percentile) {
        if (latencies.isEmpty()) return 0;
        List<Long> sorted = latencies.stream().sorted().toList();
        int index = (int) Math.ceil((percentile / 100.0) * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    @Override
    public String toString() {
        return String.format(
            "LoadTestResult{totalRequests=%d, success=%d, failure=%d, throughput=%.2f req/s, " +
            "avgLatency=%.2fms, p50=%.2fms, p95=%.2fms, p99=%.2fms, duration=%dms}",
            totalRequests, successCount, failureCount, getThroughput(),
            getAverageLatency(), getP50Latency(), getP95Latency(), getP99Latency(), totalDurationMs
        );
    }
}
