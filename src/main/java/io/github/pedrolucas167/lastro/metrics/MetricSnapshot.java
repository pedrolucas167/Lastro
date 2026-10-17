package io.github.pedrolucas167.lastro.metrics;

public class MetricSnapshot {
    private final long timestamp;
    private final double cpuLoad;
    private final int availableProcessors;
    private final long usedMemory;
    private final long totalMemory;
    private final double memoryUsagePercent;

    public MetricSnapshot(long timestamp, double cpuLoad, int availableProcessors, 
                         long usedMemory, long totalMemory, double memoryUsagePercent) {
        this.timestamp = timestamp;
        this.cpuLoad = cpuLoad;
        this.availableProcessors = availableProcessors;
        this.usedMemory = usedMemory;
        this.totalMemory = totalMemory;
        this.memoryUsagePercent = memoryUsagePercent;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public double getCpuLoad() {
        return cpuLoad;
    }

    public int getAvailableProcessors() {
        return availableProcessors;
    }

    public long getUsedMemory() {
        return usedMemory;
    }

    public long getTotalMemory() {
        return totalMemory;
    }

    public double getMemoryUsagePercent() {
        return memoryUsagePercent;
    }
}
