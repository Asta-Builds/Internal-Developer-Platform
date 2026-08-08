package com.idp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class OpaPolicyService {

    public Map<String, Object> validateScaffoldPolicy(String serviceName, String ownerTeam, String template) {
        List<String> violations = new ArrayList<>();

        if (!serviceName.matches("^[a-z0-9-]+$")) {
            violations.add("Service name must be lowercase alphanumeric with hyphens");
        }

        if (ownerTeam == null || ownerTeam.isBlank()) {
            violations.add("Mandatory FinOps Cost Center owner team tag is missing");
        }

        boolean compliant = violations.isEmpty();
        log.info("[OPA] Scaffolder Policy Validation: compliant={}, violations={}", compliant, violations.size());

        return Map.of(
            "allowed", compliant,
            "violations", violations,
            "opaEngineVersion", "0.62.0",
            "policySet", "Enterprise-IDP-Governance-v1.rego"
        );
    }
}
