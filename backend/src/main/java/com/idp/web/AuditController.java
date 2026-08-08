package com.idp.web;

import com.idp.domain.AuditLogEntryEntity;
import com.idp.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/audit")
    public ResponseEntity<List<AuditLogEntryEntity>> getAuditLogs() {
        return ResponseEntity.ok(auditService.getAuditLogs());
    }

    @GetMapping("/rbac/matrix")
    public ResponseEntity<Map<String, List<String>>> getRbacMatrix() {
        return ResponseEntity.ok(Map.of(
            "ADMIN", List.of("CREATE_SERVICE", "DELETE_SERVICE", "SCAFFOLD_PROJECT", "MUTATE_FEATURE_FLAGS", "VIEW_AUDIT_LOGS"),
            "TECH_LEAD", List.of("CREATE_SERVICE", "SCAFFOLD_PROJECT", "MUTATE_FEATURE_FLAGS", "VIEW_AUDIT_LOGS"),
            "DEVELOPER", List.of("SCAFFOLD_PROJECT", "VIEW_AUDIT_LOGS"),
            "VIEWER", List.of("READ_CATALOG", "VIEW_METRICS")
        ));
    }
}
