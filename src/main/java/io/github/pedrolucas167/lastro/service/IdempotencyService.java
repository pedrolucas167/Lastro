package io.github.pedrolucas167.lastro.service;

import io.github.pedrolucas167.lastro.domain.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IdempotencyService {
    private static final Logger logger = LoggerFactory.getLogger(IdempotencyService.class);
    private static final Duration TTL = Duration.ofHours(24);
    
    private final Map<String, IdempotencyRecord> records = new ConcurrentHashMap<>();

    public Transaction getOrRecord(String key, Instant timestamp, TransactionSupplier supplier) {
        IdempotencyRecord existing = records.get(key);
        
        if (existing != null) {
            if (existing.isExpired()) {
                records.remove(key);
                logger.info("Idempotency key {} expired, removing", key);
            } else {
                logger.info("Returning cached transaction for idempotency key {}", key);
                return existing.transaction();
            }
        }
        
        Transaction transaction = supplier.get();
        records.put(key, new IdempotencyRecord(transaction, timestamp));
        logger.info("Recorded new transaction for idempotency key {}", key);
        return transaction;
    }

    public void cleanupExpired() {
        Instant now = Instant.now();
        records.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().isExpired();
            if (expired) {
                logger.debug("Cleaning up expired idempotency key: {}", entry.getKey());
            }
            return expired;
        });
    }

    private record IdempotencyRecord(Transaction transaction, Instant timestamp) {
        boolean isExpired() {
            return Duration.between(timestamp, Instant.now()).compareTo(TTL) > 0;
        }
    }

    @FunctionalInterface
    public interface TransactionSupplier {
        Transaction get();
    }
}
