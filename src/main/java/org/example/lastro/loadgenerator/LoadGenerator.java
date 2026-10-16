package org.example.lastro.loadgenerator;

import tools.jackson.databind.ObjectMapper;
import org.example.lastro.dto.AccountResponse;
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
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class LoadGenerator {
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private final ObjectMapper objectMapper;

    public LoadGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public LoadTestResult runTransferLoadTest(String baseUrl,
                                              List<String> accountIds,
                                              int numTransfers,
                                              int concurrency) {

        if (accountIds == null || accountIds.isEmpty()) {
            throw new IllegalArgumentException("At least one account is required to run a load test");
        }

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
                    
                    String fromAccount = accountIds.get(iteration % accountIds.size());
                    String toAccount = accountIds.get((iteration + 1) % accountIds.size());

                    String body = objectMapper.writeValueAsString(Map.of(
                        "fromAccountId", fromAccount,
                        "toAccountId", toAccount,
                        "amount", new BigDecimal("100.00")
                    ));

                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/transactions/transfer"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
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

    public List<String> createTestAccounts(String baseUrl, int numAccounts) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, Math.min(10, numAccounts)));
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < numAccounts; i++) {
            final int accountId = i;
            futures.add(executor.submit(() -> {
                try {
                    String body = objectMapper.writeValueAsString(
                        Map.of("initialBalance", new BigDecimal("10000.00")));

                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/accounts"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                    HttpResponse<String> response = httpClient.send(request, 
                        HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() != 200) {
                        throw new IllegalStateException(
                            "Failed to create account " + accountId + ": HTTP " + response.statusCode());
                    }

                    return objectMapper.readValue(response.body(), AccountResponse.class).getId();
                } catch (Exception e) {
                    throw new IllegalStateException(
                        "Failed to create account " + accountId + ": " + e.getMessage(), e);
                }
            }));
        }

        List<String> accountIds = new ArrayList<>();
        for (Future<String> future : futures) {
            accountIds.add(future.get());
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
        return accountIds;
    }
}
