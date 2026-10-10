package org.example.lastro.loadgenerator;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class LoadGenerator {
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    public LoadTestResult runTransferLoadTest(String baseUrl, 
                                              int numAccounts, 
                                              int numTransfers, 
                                              int concurrency) {
        
        Instant startTime = Instant.now();
        AtomicLong successCount = new AtomicLong(0);
        AtomicLong failureCount = new AtomicLong(0);
        List<Long> latencies = new CopyOnWriteArrayList<>();
        
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < numTransfers; i++) {
            final int iteration = i;
            futures.add(executor.submit(() -> {
                try {
                    Instant requestStart = Instant.now();
                    
                    String fromAccount = "account-" + (iteration % numAccounts);
                    String toAccount = "account-" + ((iteration + 1) % numAccounts);
                    
                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/transactions/transfer"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(
                            "fromAccountId=" + fromAccount + 
                            "&toAccountId=" + toAccount + 
                            "&amount=100.00"))
                        .build();

                    HttpResponse<String> response = httpClient.send(request, 
                        HttpResponse.BodyHandlers.ofString());
                    
                    Instant requestEnd = Instant.now();
                    long latency = Duration.between(requestStart, requestEnd).toMillis();
                    latencies.add(latency);

                    if (response.statusCode() == 200) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
            }));
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            }
        }

        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Instant endTime = Instant.now();
        long totalDuration = Duration.between(startTime, endTime).toMillis();

        return new LoadTestResult(
            numTransfers,
            successCount.get(),
            failureCount.get(),
            totalDuration,
            latencies
        );
    }

    public void createTestAccounts(String baseUrl, int numAccounts) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < numAccounts; i++) {
            final int accountId = i;
            futures.add(executor.submit(() -> {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/accounts"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString("initialBalance=10000.00"))
                        .build();

                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                } catch (Exception e) {
                    System.err.println("Failed to create account " + accountId + ": " + e.getMessage());
                }
            }));
        }

        for (Future<?> future : futures) {
            future.get();
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
    }
}
