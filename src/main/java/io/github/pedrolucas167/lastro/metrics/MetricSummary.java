package io.github.pedrolucas167.lastro.metrics;

public class MetricSummary {
    private final int sampleCount;
    private final double avgCpuLoad;
    private final double maxCpuLoad;
    private final double avgMemoryUsage;
    private final double maxMemoryUsage;
    private final long durationMs;

    public MetricSummary(int sampleCount, double avgCpuLoad, double maxCpuLoad, 
                        double avgMemoryUsage, double maxMemoryUsage, long durationMs) {
        this.sampleCount = sampleCount;
        this.avgCpuLoad = avgCpuLoad;
        this.maxCpuLoad = maxCpuLoad;
        this.avgMemoryUsage = avgMemoryUsage;
        this.maxMemoryUsage = maxMemoryUsage;
        this.durationMs = durationMs;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    public double getAvgCpuLoad() {
        return avgCpuLoad;
    }

    public double getMaxCpuLoad() {
        return maxCpuLoad;
    }

    public double getAvgMemoryUsage() {
        return avgMemoryUsage;
    }

    public double getMaxMemoryUsage() {
        return maxMemoryUsage;
    }

    public long getDurationMs() {
        return durationMs;
    }

    @Override
    public String toString() {
        return String.format(
            "MetricSummary{samples=%d, avgCpu=%.2f%%, maxCpu=%.2f%%, avgMemory=%.2f%%, maxMemory=%.2f%%, duration=%dms}",
            sampleCount, avgCpuLoad, maxCpuLoad, avgMemoryUsage, maxMemoryUsage, durationMs
        );
    }
}
