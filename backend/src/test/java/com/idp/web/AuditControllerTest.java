package com.idp.web;

import com.idp.domain.Role;
import com.idp.domain.RolePermissionEntity;
import com.idp.repository.RolePermissionRepository;
import com.idp.security.AuthenticatedUser;
import com.idp.service.AuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The audit endpoint is team-scoped via the bound principal; the RBAC matrix is
 * rendered from the permission table.
 */
@ExtendWith(MockitoExtension.class)
class AuditControllerTest {

    @Mock private AuditService auditService;
    @Mock private RolePermissionRepository rolePermissionRepository;

    private AuditController controller;

    private final AuthenticatedUser admin = AuthenticatedUser.builder()
            .id("usr-1").username("root").role(Role.ADMIN).team("Platform").build();

    @BeforeEach
    void setUp() {
        controller = new AuditController(auditService, rolePermissionRepository);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                admin, null, List.of()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("returns the audit trail scoped to the caller")
    void auditTrail() {
        when(auditService.getAuditLogsFor(admin)).thenReturn(List.of());

        assertThat(controller.getAuditLogs().getBody()).isEmpty();
    }

    @Test
    @DisplayName("renders the matrix from the permission table for all roles")
    void rbacMatrix() {
        when(rolePermissionRepository.findByRole(Role.ADMIN)).thenReturn(List.of(
                RolePermissionEntity.builder().action(com.idp.domain.PermissionAction.MANAGE)
                        .resourceType(com.idp.domain.ResourceType.ADMIN).build()));
        when(rolePermissionRepository.findByRole(Role.TECH_LEAD)).thenReturn(List.of());
        when(rolePermissionRepository.findByRole(Role.DEVELOPER)).thenReturn(List.of(
                RolePermissionEntity.builder().action(com.idp.domain.PermissionAction.READ)
                        .resourceType(com.idp.domain.ResourceType.SERVICE).build()));
        when(rolePermissionRepository.findByRole(Role.VIEWER)).thenReturn(List.of());

        Map<String, List<String>> matrix = controller.getRbacMatrix().getBody();

        assertThat(matrix.keySet()).containsExactly("ADMIN", "TECH_LEAD", "DEVELOPER", "VIEWER");
        assertThat(matrix.get("ADMIN")).containsExactly("MANAGE_ADMIN");
        assertThat(matrix.get("DEVELOPER")).containsExactly("READ_SERVICE");
    }
}
