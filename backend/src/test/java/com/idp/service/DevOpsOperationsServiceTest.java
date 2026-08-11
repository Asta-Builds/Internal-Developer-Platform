package com.idp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Operational surfaces: cluster status, vulnerability scans and FinOps reporting.
 */
class DevOpsOperationsServiceTest {

    private DevOpsOperationsService service;

    @BeforeEach
    void setUp() {
        service = new DevOpsOperationsService();
    }

    @Test
    @DisplayName("reports a healthy cluster with running pods")
    void clusterStatus() {
        var cluster = service.getKubernetesClusterStatus();

        assertThat(cluster.get("clusterName")).isEqualTo("k8s-prod-eu-west-1");
        assertThat(cluster.get("totalNodes")).isEqualTo(12);
        assertThat(cluster.get("healthyNodes")).isEqualTo(12);
        @SuppressWarnings("unchecked")
        var pods = (List<Map<String, Object>>) cluster.get("runningPods");
        assertThat(pods).allMatch(pod -> "RUNNING".equals(pod.get("status")));
    }

    @Test
    @DisplayName("security scans carry the CVE summary and image results")
    void securityScans() {
        var scan = service.getSecurityVulnerabilitiesScan();

        assertThat(scan.get("scanner")).asString().contains("Trivy");
        @SuppressWarnings("unchecked")
        var summary = (Map<String, Object>) scan.get("cveSummary");
        assertThat(summary.get("CRITICAL")).isEqualTo(0);
        assertThat(summary.get("HIGH")).isEqualTo(0);
    }

    @Test
    @DisplayName("the FinOps report carries costs and savings recommendations")
    void finOpsReport() {
        var report = service.getFinOpsCostReport();

        assertThat(report.get("currency")).isEqualTo("USD");
        assertThat(report.get("monthlyTotalCost")).isEqualTo(1450.50);
        assertThat(report.get("finOpsSavingsRecommendation")).asString().contains("Savings Plans");
    }

    @Test
    @DisplayName("triggerPipelineRun returns a queued build id")
    void triggersPipeline() {
        var result = service.triggerPipelineRun("payment-service", "main");

        assertThat(result.get("repository")).isEqualTo("payment-service");
        assertThat(result.get("branch")).isEqualTo("main");
        assertThat(result.get("status")).isEqualTo("QUEUED");
        assertThat(result.get("buildId")).asString().startsWith("build-");
    }
}
