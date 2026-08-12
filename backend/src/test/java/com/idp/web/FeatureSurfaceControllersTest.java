package com.idp.web;

import com.idp.dto.CopilotChatRequestDto;
import com.idp.dto.CopilotChatResponseDto;
import com.idp.dto.ServiceHealthDto;
import com.idp.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Copilot, RAG, observability, devops, DLQ and enterprise status endpoints.
 */
@ExtendWith(MockitoExtension.class)
class FeatureSurfaceControllersTest {

    @Mock private CopilotService copilotService;
    @Mock private ObservabilityService observabilityService;

    private CopilotController copilotController;
    private RagController ragController;
    private ObservabilityController observabilityController;
    private DevOpsController devOpsController;
    private EnterpriseController enterpriseController;
    private EventReplayService eventReplayService;

    @BeforeEach
    void setUp() {
        copilotController = new CopilotController(copilotService);
        ragController = new RagController(copilotService);
        observabilityController = new ObservabilityController(observabilityService);
        devOpsController = new DevOpsController(new DevOpsOperationsService());

        IdempotencyService idempotencyService = new IdempotencyService();
        AuditService auditService = mock(AuditService.class);
        eventReplayService = new EventReplayService(idempotencyService, auditService);

        enterpriseController = new EnterpriseController(new ResiliencePolicyService(), eventReplayService);
    }

    @Test
    @DisplayName("copilot chat delegates to the service")
    void copilotChat() {
        CopilotChatRequestDto request = CopilotChatRequestDto.builder().query("payment").build();
        CopilotChatResponseDto response = CopilotChatResponseDto.builder()
                .answer("answer").sources(List.of("src")).confidenceScore(0.9).build();
        when(copilotService.processQuery(request)).thenReturn(response);

        assertThat(copilotController.chat(request).getBody()).isEqualTo(response);
    }

    @Test
    @DisplayName("copilot suggested questions are served")
    void copilotSuggestions() {
        when(copilotService.getSuggestedQuestions()).thenReturn(List.of("q1"));

        assertThat(copilotController.getSuggestedQuestions().getBody()).containsExactly("q1");
    }

    @Test
    @DisplayName("rag endpoints query, list sources, ingest and stream")
    void ragEndpoints() {
        CopilotChatRequestDto request = CopilotChatRequestDto.builder().query("payment").build();
        CopilotChatResponseDto response = CopilotChatResponseDto.builder()
                .answer("answer").sources(List.of("src")).confidenceScore(0.9).build();
        when(copilotService.processQuery(request)).thenReturn(response);
        when(copilotService.getIndexedSources()).thenReturn(List.of(Map.of("id", "doc-1", "title", "Payment Specs")));
        when(copilotService.reindexDocumentation(null)).thenReturn(Map.of("status", "INGESTION_COMPLETED"));

        assertThat(ragController.query(request).getBody()).isEqualTo(response);
        assertThat(ragController.getSources().getBody()).hasSize(1);
        assertThat(ragController.ingest(null).getBody().get("status")).isEqualTo("INGESTION_COMPLETED");
        assertThat(ragController.streamQuery("payment")).isNotNull();
    }

    @Test
    @DisplayName("service health is served per service")
    void serviceHealth() {
        ServiceHealthDto health = ServiceHealthDto.builder().serviceId("srv-1").status("HEALTHY").build();
        when(observabilityService.getServiceHealth("srv-1")).thenReturn(health);

        assertThat(observabilityController.getServiceHealth("srv-1").getBody()).isEqualTo(health);
    }

    @Test
    @DisplayName("telemetry metrics are served")
    void telemetry() {
        when(observabilityService.getTelemetryMetrics()).thenReturn(Map.of("totalPods", 6));

        assertThat(observabilityController.getTelemetryMetrics().getBody().get("totalPods")).isEqualTo(6);
    }

    @Test
    @DisplayName("log streaming produces an SSE response")
    void logStream() {
        when(observabilityService.streamLiveLogs("srv-1"))
                .thenReturn(new org.springframework.web.servlet.mvc.method.annotation.SseEmitter());

        assertThat(observabilityController.streamLiveLogs("srv-1")).isNotNull();
    }

    @Test
    @DisplayName("devops surfaces expose cluster, scans and costs")
    void devopsSurfaces() {
        assertThat(devOpsController.getK8sStatus().getBody().get("clusterName")).isEqualTo("k8s-prod-eu-west-1");
        assertThat(devOpsController.getSecurityScans().getBody().get("scanner")).asString().contains("Trivy");
        assertThat(devOpsController.getFinOpsReport().getBody().get("currency")).isEqualTo("USD");
        assertThat(devOpsController.triggerPipeline("pay", "main").getBody().get("status")).isEqualTo("QUEUED");
    }

    @Test
    @DisplayName("enterprise status and DLQ surfaces are served")
    void enterpriseSurfaces() {
        assertThat(enterpriseController.getResilience().getBody().get("k8sCircuitBreaker")).isEqualTo("CLOSED");
        assertThat(enterpriseController.getStatusPage().getBody().get("platformStatus"))
                .isEqualTo("ALL_SYSTEMS_OPERATIONAL");

        Map<String, Object> dlq = enterpriseController.getDlqStatus().getBody();
        assertThat(dlq.get("deadLetterExchange")).isEqualTo("idp.deadletter.exchange");
        assertThat(dlq.get("deadLetterQueue")).isEqualTo("fraud.analysis.dlq");
    }

    @Test
    @DisplayName("event replay executes and deduplicates duplicate invocations")
    void eventReplayAndDeduplication() {
        Map<String, Object> payload = Map.of(
                "eventId", "evt-123",
                "idempotencyKey", "idem-key-abc",
                "dryRun", false,
                "payload", Map.of("action", "retry_payment", "amount", 500)
        );

        ResponseEntity<Map<String, Object>> firstRun = enterpriseController.replayEvent(payload);
        assertThat(firstRun.getBody().get("status")).isEqualTo("REPLAYED_SUCCESS");
        assertThat(firstRun.getBody().get("duplicate")).isEqualTo(false);

        // Second invocation with same idempotencyKey -> must deduplicate without re-executing
        ResponseEntity<Map<String, Object>> secondRun = enterpriseController.replayEvent(payload);
        assertThat(secondRun.getBody().get("status")).isEqualTo("DEDUPLICATED_SKIPPED");
        assertThat(secondRun.getBody().get("duplicate")).isEqualTo(true);
    }

    @Test
    @DisplayName("dry-run simulation executes without recording duplicate idempotency lock")
    void dryRunSimulation() {
        Map<String, Object> payload = Map.of(
                "eventId", "evt-dry-456",
                "idempotencyKey", "idem-dry-key",
                "payload", Map.of("action", "test_routing")
        );

        ResponseEntity<Map<String, Object>> sim = enterpriseController.dryRunSimulation(payload);
        assertThat(sim.getBody().get("status")).isEqualTo("SIMULATED_SUCCESS");
        assertThat(sim.getBody().get("dryRun")).isEqualTo(true);
    }
}
