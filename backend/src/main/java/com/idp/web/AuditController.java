package com.idp.web;

import com.idp.domain.AuditLogEntryEntity;
import com.idp.domain.Role;
import com.idp.domain.RolePermissionEntity;
import com.idp.repository.RolePermissionRepository;
import com.idp.security.CurrentUser;
import com.idp.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;
    private final RolePermissionRepository rolePermissionRepository;

    /**
     * Scoped to the caller's team unless they are ADMIN, per the platform policy.
     */
    @GetMapping("/audit")
    @PreAuthorize("hasPermission(null, 'AUDIT_LOG', 'READ')")
    public ResponseEntity<List<AuditLogEntryEntity>> getAuditLogs() {
        return ResponseEntity.ok(auditService.getAuditLogsFor(CurrentUser.require()));
    }

    /**
     * The live RBAC matrix, read from {@code role_permissions}. Previously a hardcoded
     * literal that could drift from the rules actually enforced.
     */
    @GetMapping("/rbac/matrix")
    @PreAuthorize("hasPermission(null, 'SERVICE', 'READ')")
    public ResponseEntity<Map<String, List<String>>> getRbacMatrix() {
        Map<String, List<String>> matrix = new LinkedHashMap<>();
        for (Role role : Arrays.asList(Role.ADMIN, Role.TECH_LEAD, Role.DEVELOPER, Role.VIEWER)) {
            matrix.put(role.name(), rolePermissionRepository.findByRole(role).stream()
                    .map(this::toPermissionLabel)
                    .sorted()
                    .collect(Collectors.toList()));
        }
        return ResponseEntity.ok(matrix);
    }

    private String toPermissionLabel(RolePermissionEntity permission) {
        return permission.getAction() + "_" + permission.getResourceType();
    }
}
