package org.example.lastro.api;

import org.example.lastro.loadgenerator.LoadGenerator;
import org.example.lastro.loadgenerator.LoadTestResult;
import org.example.lastro.metrics.MetricSummary;
import org.example.lastro.metrics.MetricsCollector;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/loadtest")
public class LoadTestController {
    private final LoadGenerator loadGenerator;
    private final MetricsCollector metricsCollector;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public LoadTestController(LoadGenerator loadGenerator, MetricsCollector metricsCollector) {
        this.loadGenerator = loadGenerator;
        this.metricsCollector = metricsCollector;
    }

    @PostMapping("/run")
    public ResponseEntity<LoadTestReport> runLoadTest(
            @RequestParam(defaultValue = "http://localhost:8080") String baseUrl,
            @RequestParam(defaultValue = "10") int numAccounts,
            @RequestParam(defaultValue = "1000") int numTransfers,
            @RequestParam(defaultValue = "10") int concurrency,
            @RequestParam(defaultValue = "100") int metricsIntervalMs) {

        metricsCollector.clearSnapshots();

        try {
            loadGenerator.createTestAccounts(baseUrl, numAccounts);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                new LoadTestReport(null, null, "Failed to create test accounts: " + e.getMessage())
            );
        }

        scheduler.scheduleAtFixedRate(() -> metricsCollector.collectSnapshot(), 
            0, metricsIntervalMs, TimeUnit.MILLISECONDS);

        LoadTestResult result = loadGenerator.runTransferLoadTest(baseUrl, numAccounts, numTransfers, concurrency);
        
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        MetricSummary metricsSummary = metricsCollector.getSummary();

        LoadTestReport report = new LoadTestReport(result, metricsSummary, null);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/metrics")
    public ResponseEntity<MetricSummary> getMetricsSummary() {
        return ResponseEntity.ok(metricsCollector.getSummary());
    }

    @PostMapping("/metrics/clear")
    public ResponseEntity<Void> clearMetrics() {
        metricsCollector.clearSnapshots();
        return ResponseEntity.ok().build();
    }

    public record LoadTestReport(
        LoadTestResult loadTestResult,
        MetricSummary metricSummary,
        String error
    ) {}
}
