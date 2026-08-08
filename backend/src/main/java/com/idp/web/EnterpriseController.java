package com.idp.web;

import com.idp.service.AbacPolicyEngine;
import com.idp.service.OpaPolicyService;
import com.idp.service.ResiliencePolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/enterprise")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EnterpriseController {

    private final AbacPolicyEngine abacPolicyEngine;
    private final OpaPolicyService opaPolicyService;
    private final ResiliencePolicyService resiliencePolicyService;

    @GetMapping("/abac/eval")
    public ResponseEntity<Map<String, Object>> evalAbac(
            @RequestParam(defaultValue = "DEVELOPER") String role,
            @RequestParam(defaultValue = "Equipe Platform") String team,
            @RequestParam(defaultValue = "PROD") String env,
            @RequestParam(defaultValue = "CRITICAL") String criticality,
            @RequestParam(defaultValue = "DELETE_SERVICE") String action) {

        boolean allowed = abacPolicyEngine.evaluateAccess(role, team, env, criticality, action);
        return ResponseEntity.ok(Map.of(
            "allowed", allowed,
            "role", role,
            "team", team,
            "env", env,
            "criticality", criticality,
            "action", action,
            "abacSummary", abacPolicyEngine.getAbacRulesSummary()
        ));
    }

    @GetMapping("/opa/validate")
    public ResponseEntity<Map<String, Object>> validateOpa(
            @RequestParam(defaultValue = "payment-gateway") String name,
            @RequestParam(defaultValue = "Equipe Paiement") String team,
            @RequestParam(defaultValue = "SPRING_BOOT") String template) {

        return ResponseEntity.ok(opaPolicyService.validateScaffoldPolicy(name, team, template));
    }

    @GetMapping("/resilience")
    public ResponseEntity<Map<String, Object>> getResilience() {
        return ResponseEntity.ok(resiliencePolicyService.getResilienceStatus());
    }

    @GetMapping("/status-page")
    public ResponseEntity<Map<String, Object>> getStatusPage() {
        return ResponseEntity.ok(resiliencePolicyService.getInternalStatusPage());
    }
}
