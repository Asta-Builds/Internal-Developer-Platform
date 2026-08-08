package com.idp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class ResiliencePolicyService {

    public Map<String, Object> getResilienceStatus() {
        return Map.of(
            "k8sCircuitBreaker", "CLOSED",
            "githubApiCircuitBreaker", "CLOSED",
            "prometheusCircuitBreaker", "CLOSED",
            "activeTenantQuotas", Map.of(
                "maxProjectsPerTenant", 50,
                "ragQueriesRateLimitPerMinute", 100,
                "currentTenantUsagePercent", 14
            ),
            "disasterRecovery", Map.of(
                "rtoMinutes", 15,
                "rpoMinutes", 5,
                "multiAzFailover", "ACTIVE"
            )
        );
    }

    public Map<String, Object> getInternalStatusPage() {
        return Map.of(
            "platformStatus", "ALL_SYSTEMS_OPERATIONAL",
            "services", Map.of(
                "idpCoreBackend", "OPERATIONAL (99.99%)",
                "keycloakIam", "OPERATIONAL (99.99%)",
                "scaffolderEngine", "OPERATIONAL (100%)",
                "observabilityStream", "OPERATIONAL (100%)",
                "idpCopilotRag", "OPERATIONAL (99.95%)"
            ),
            "lastIncidentTimestamp", System.currentTimeMillis() - 864000000L
        );
    }
}
