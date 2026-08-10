package com.idp.web;

import com.idp.service.ResiliencePolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Operational status surface.
 *
 * <p>The former {@code /abac/eval} and {@code /opa/validate} endpoints were removed:
 * they accepted the caller's own role and team as query parameters, so they simulated
 * a decision rather than enforcing one. Real evaluation now lives in
 * {@code PolicyDecisionService}, and the ADMIN-only simulator is
 * {@code POST /api/v1/admin/policies/simulate}.
 */
@RestController
@RequestMapping("/api/v1/enterprise")
@RequiredArgsConstructor
public class EnterpriseController {

    private final ResiliencePolicyService resiliencePolicyService;

    @GetMapping("/resilience")
    @PreAuthorize("hasPermission(null, 'DEVOPS', 'READ')")
    public ResponseEntity<Map<String, Object>> getResilience() {
        return ResponseEntity.ok(resiliencePolicyService.getResilienceStatus());
    }

    @GetMapping("/status-page")
    @PreAuthorize("hasPermission(null, 'DEVOPS', 'READ')")
    public ResponseEntity<Map<String, Object>> getStatusPage() {
        return ResponseEntity.ok(resiliencePolicyService.getInternalStatusPage());
    }
}
