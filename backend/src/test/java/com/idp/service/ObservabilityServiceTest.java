package com.idp.service;

import com.idp.dto.ServiceHealthDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Health snapshots and cluster telemetry for the observability surface.
 */
class ObservabilityServiceTest {

    private ObservabilityService service;

    @BeforeEach
    void setUp() {
        service = new ObservabilityService();
    }

    @Test
    @DisplayName("a health snapshot names the service and reports HEALTHY")
    void healthSnapshot() {
        ServiceHealthDto health = service.getServiceHealth("srv-payment");

        assertThat(health.getServiceId()).isEqualTo("srv-payment");
        assertThat(health.getServiceName()).isEqualTo("PAYMENT");
        assertThat(health.getStatus()).isEqualTo("HEALTHY");
        assertThat(health.getCpuUsagePercent()).isBetween(2.0, 10.0);
        assertThat(health.getMemoryUsageMb()).isBetween(120L, 159L);
        assertThat(health.getActivePodCount()).isEqualTo(2);
        assertThat(health.getTimestamp()).isPositive();
    }

    @Test
    @DisplayName("telemetry metrics carry the cluster summary")
    void telemetryMetrics() {
        var metrics = service.getTelemetryMetrics();

        assertThat(metrics.get("totalPods")).isEqualTo(6);
        assertThat(metrics.get("healthyPods")).isEqualTo(6);
        assertThat(metrics.get("uptimePercent")).isEqualTo(99.99);
        assertThat(metrics.get("prometheusScrapeInterval")).isEqualTo("15s");
    }

    @Test
    @DisplayName("log streaming returns an emitter")
    void logStreamingEmitter() {
        assertThat(service.streamLiveLogs("srv-payment")).isNotNull();
        assertThat(service.streamLiveLogs(null)).isNotNull();
    }
}
