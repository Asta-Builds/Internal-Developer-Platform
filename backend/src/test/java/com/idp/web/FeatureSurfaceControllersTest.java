package com.idp.web;

import com.idp.dto.CopilotChatRequestDto;
import com.idp.dto.CopilotChatResponseDto;
import com.idp.dto.ServiceHealthDto;
import com.idp.service.CopilotService;
import com.idp.service.ObservabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Copilot, observability, devops and enterprise status endpoints.
 */
@ExtendWith(MockitoExtension.class)
class FeatureSurfaceControllersTest {

    @Mock private CopilotService copilotService;
    @Mock private ObservabilityService observabilityService;

    private CopilotController copilotController;
    private ObservabilityController observabilityController;
    private DevOpsController devOpsController;
    private EnterpriseController enterpriseController;

    @BeforeEach
    void setUp() {
        copilotController = new CopilotController(copilotService);
        observabilityController = new ObservabilityController(observabilityService);
        devOpsController = new DevOpsController(new com.idp.service.DevOpsOperationsService());
        enterpriseController = new EnterpriseController(new com.idp.service.ResiliencePolicyService());
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
    @DisplayName("enterprise status surfaces are served")
    void enterpriseSurfaces() {
        assertThat(enterpriseController.getResilience().getBody().get("k8sCircuitBreaker")).isEqualTo("CLOSED");
        assertThat(enterpriseController.getStatusPage().getBody().get("platformStatus"))
                .isEqualTo("ALL_SYSTEMS_OPERATIONAL");
    }
}
