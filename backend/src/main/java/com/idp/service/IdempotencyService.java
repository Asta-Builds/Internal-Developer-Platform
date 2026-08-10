package com.idp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise Idempotency Service for guaranteeing safe retries across REST mutations.
 * Tracks processed Idempotency-Key headers and caches operation results.
 */
@Service
@Slf4j
public class IdempotencyService {

    private final Map<String, Object> idempotencyCache = new ConcurrentHashMap<>();

    public Optional<Object> getCachedResult(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        Object result = idempotencyCache.get(idempotencyKey);
        if (result != null) {
            log.info("Idempotency hit for key: {}. Returning cached payload.", idempotencyKey);
        }
        return Optional.ofNullable(result);
    }

    public void storeResult(String idempotencyKey, Object result) {
        if (idempotencyKey != null && !idempotencyKey.isBlank() && result != null) {
            idempotencyCache.put(idempotencyKey, result);
            log.debug("Stored idempotency record for key: {}", idempotencyKey);
        }
    }
}
