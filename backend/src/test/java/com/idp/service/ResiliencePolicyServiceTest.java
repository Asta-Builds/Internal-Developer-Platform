package com.idp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Static resilience and status-page surfaces.
 */
class ResiliencePolicyServiceTest {

    private ResiliencePolicyService service;

    @BeforeEach
    void setUp() {
        service = new ResiliencePolicyService();
    }

    @Test
    @DisplayName("reports all circuit breakers closed and quotas set")
    void resilienceStatus() {
        var status = service.getResilienceStatus();

        assertThat(status.get("k8sCircuitBreaker")).isEqualTo("CLOSED");
        assertThat(status.get("githubApiCircuitBreaker")).isEqualTo("CLOSED");
        assertThat(status.get("prometheusCircuitBreaker")).isEqualTo("CLOSED");
        @SuppressWarnings("unchecked")
        var quotas = (java.util.Map<String, Object>) status.get("activeTenantQuotas");
        assertThat(quotas.get("maxProjectsPerTenant")).isEqualTo(50);
    }

    @Test
    @DisplayName("the status page reports all systems operational")
    void statusPage() {
        var page = service.getInternalStatusPage();

        assertThat(page.get("platformStatus")).isEqualTo("ALL_SYSTEMS_OPERATIONAL");
        @SuppressWarnings("unchecked")
        var services = (java.util.Map<String, Object>) page.get("services");
        assertThat(services).containsKeys("idpCoreBackend", "keycloakIam", "scaffolderEngine");
    }
}
