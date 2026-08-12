package com.idp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EventReplayService covering dry-run, idempotency deduplication, and DLQ reprocessing.
 */
class EventReplayServiceTest {

    private IdempotencyService idempotencyService;
    private AuditService auditService;
    private EventReplayService eventReplayService;

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService();
        auditService = mock(AuditService.class);
        eventReplayService = new EventReplayService(idempotencyService, auditService);
    }

    @Test
    @DisplayName("dry-run simulation returns simulated success without writing audit records")
    void dryRunSimulation() {
        Map<String, Object> payload = Map.of("transactionId", "txn-test", "amount", 100);
        Map<String, Object> result = eventReplayService.replayEvent("evt-1", "idem-1", payload, true);

        assertThat(result.get("status")).isEqualTo("SIMULATED_SUCCESS");
        assertThat(result.get("dryRun")).isEqualTo(true);
        assertThat(result.get("duplicate")).isEqualTo(false);
        verify(auditService, never()).logAction(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("live replay executes successfully and records idempotency state")
    void liveReplaySuccess() {
        Map<String, Object> payload = Map.of("transactionId", "txn-live", "amount", 250);
        Map<String, Object> result = eventReplayService.replayEvent("evt-2", "idem-2", payload, false);

        assertThat(result.get("status")).isEqualTo("REPLAYED_SUCCESS");
        assertThat(result.get("duplicate")).isEqualTo(false);
        verify(auditService).logAction(eq("SYSTEM_OPERATOR"), eq("EVENT_REPLAYED"), eq("evt-2"), anyString());
    }

    @Test
    @DisplayName("duplicate event replay is safely intercepted and deduplicated")
    void duplicateReplayIsDeduplicated() {
        Map<String, Object> payload = Map.of("transactionId", "txn-dup", "amount", 300);

        Map<String, Object> first = eventReplayService.replayEvent("evt-3", "idem-3", payload, false);
        assertThat(first.get("status")).isEqualTo("REPLAYED_SUCCESS");

        Map<String, Object> second = eventReplayService.replayEvent("evt-3", "idem-3", payload, false);
        assertThat(second.get("status")).isEqualTo("DEDUPLICATED_SKIPPED");
        assertThat(second.get("duplicate")).isEqualTo(true);

        // Audit log should only have been written once for the initial successful replay
        verify(auditService, times(1)).logAction(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("getDlqStatus exposes retry policy parameters and DLQ routing config")
    void dlqStatus() {
        Map<String, Object> status = eventReplayService.getDlqStatus();

        assertThat(status.get("deadLetterExchange")).isEqualTo("idp.deadletter.exchange");
        assertThat(status.get("deadLetterQueue")).isEqualTo("fraud.analysis.dlq");
        assertThat(status.get("retryPolicy")).isInstanceOf(Map.class);
    }
}
