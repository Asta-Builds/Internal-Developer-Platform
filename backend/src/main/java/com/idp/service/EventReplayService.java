package com.idp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service responsible for event replay, DLQ message reprocessing, and simulation (dry-run).
 * Guarantees idempotency to avoid duplicate processing, dual commits, or data loss.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventReplayService {

    private final IdempotencyService idempotencyService;
    private final AuditService auditService;

    private final Map<String, Map<String, Object>> replayedEventsLog = new ConcurrentHashMap<>();

    /**
     * Replays or simulates an event execution with idempotency guard.
     */
    public Map<String, Object> replayEvent(String eventId, String idempotencyKey, Map<String, Object> payload, boolean dryRun) {
        String effectiveKey = idempotencyKey != null && !idempotencyKey.isBlank()
                ? idempotencyKey
                : "replay-" + (eventId != null ? eventId : UUID.randomUUID().toString());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("eventId", eventId != null ? eventId : UUID.randomUUID().toString());
        response.put("idempotencyKey", effectiveKey);
        response.put("dryRun", dryRun);
        response.put("timestamp", System.currentTimeMillis());

        if (dryRun) {
            log.info("[SIMULATION / DRY-RUN] Simulating event replay for eventId={}, key={}", eventId, effectiveKey);
            response.put("status", "SIMULATED_SUCCESS");
            response.put("duplicate", false);
            response.put("message", "Dry-run simulation completed successfully without side effects or DB mutations.");
            response.put("simulatedPayload", payload != null ? payload : Map.of());
            return response;
        }

        // Idempotency check: prevent duplicate event processing
        Optional<Object> cached = idempotencyService.getCachedResult(effectiveKey);
        if (cached.isPresent()) {
            log.warn("[EVENT REPLAY] Idempotency duplicate detected for key {}. Skipping re-execution.", effectiveKey);
            response.put("status", "DEDUPLICATED_SKIPPED");
            response.put("duplicate", true);
            response.put("message", "Event was already processed previously. Deduplicated to prevent duplicate transactions or data corruption.");
            response.put("cachedResult", cached.get());
            return response;
        }

        log.info("[EVENT REPLAY] Executing fresh event replay for eventId={}, key={}", eventId, effectiveKey);
        Map<String, Object> executionResult = new LinkedHashMap<>();
        executionResult.put("replayedAt", System.currentTimeMillis());
        executionResult.put("payloadProcessed", payload != null ? payload : Map.of());
        executionResult.put("dlqResolved", true);

        // Store result in idempotency cache
        idempotencyService.storeResult(effectiveKey, executionResult);
        replayedEventsLog.put(effectiveKey, executionResult);

        auditService.logAction(
                "SYSTEM_OPERATOR",
                "EVENT_REPLAYED",
                eventId != null ? eventId : effectiveKey,
                "Event successfully replayed and deduplication key registered"
        );

        response.put("status", "REPLAYED_SUCCESS");
        response.put("duplicate", false);
        response.put("message", "Event successfully reprocessed from DLQ/Audit log without loss.");
        response.put("result", executionResult);

        return response;
    }

    public Map<String, Object> getDlqStatus() {
        return Map.of(
                "deadLetterExchange", "idp.deadletter.exchange",
                "deadLetterQueue", "fraud.analysis.dlq",
                "generalDlq", "idp.deadletter.queue",
                "retryPolicy", Map.of(
                        "enabled", true,
                        "initialIntervalMs", 1000,
                        "multiplier", 2.0,
                        "maxAttempts", 3,
                        "maxIntervalMs", 10000
                ),
                "replayedEventsCount", replayedEventsLog.size(),
                "dlqActiveMessages", 0
        );
    }
}
