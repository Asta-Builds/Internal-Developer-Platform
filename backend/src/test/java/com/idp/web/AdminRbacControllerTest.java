package com.idp.web;

import com.idp.domain.AbacPolicyEntity;
import com.idp.domain.PermissionAction;
import com.idp.domain.PolicyEffect;
import com.idp.domain.ResourceType;
import com.idp.domain.Role;
import com.idp.domain.RolePermissionEntity;
import com.idp.domain.UserEntity;
import com.idp.repository.AbacPolicyRepository;
import com.idp.repository.RolePermissionRepository;
import com.idp.repository.UserRepository;
import com.idp.security.AccessDecision;
import com.idp.security.AuthenticatedUser;
import com.idp.security.PolicyDecisionService;
import com.idp.security.ResourceAttributeResolver;
import com.idp.service.AuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The platform's own authorization administration surface.
 */
@ExtendWith(MockitoExtension.class)
class AdminRbacControllerTest {

    @Mock private UserRepository userRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private AbacPolicyRepository abacPolicyRepository;
    @Mock private PolicyDecisionService policyDecisionService;
    @Mock private ResourceAttributeResolver resourceAttributeResolver;
    @Mock private AuditService auditService;

    private AdminRbacController controller;

    private final AuthenticatedUser admin = AuthenticatedUser.builder()
            .id("usr-1").username("root").role(Role.ADMIN).team("Platform").build();
    private final UserEntity targetUser = UserEntity.builder()
            .id("usr-5").username("eve").email("eve@x.io").role(Role.VIEWER).active(true).build();

    @BeforeEach
    void setUp() {
        controller = new AdminRbacController(userRepository, rolePermissionRepository,
                abacPolicyRepository, policyDecisionService, resourceAttributeResolver, auditService);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                admin, null, List.of()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("lists users")
    void listsUsers() {
        when(userRepository.findAll()).thenReturn(List.of(targetUser));

        assertThat(controller.listUsers().getBody()).containsExactly(targetUser);
    }

    @Test
    @DisplayName("changes a user role and invalidates the policy cache")
    void changesUserRole() {
        when(userRepository.findById("usr-5")).thenReturn(Optional.of(targetUser));

        var response = controller.updateUserRole("usr-5", Map.of("role", "TECH_LEAD", "team", "Equipe Paiement"));

        assertThat(response.getBody().getRole()).isEqualTo(Role.TECH_LEAD);
        assertThat(response.getBody().getTeam()).isEqualTo("Equipe Paiement");
        verify(policyDecisionService).invalidatePolicyCache();
        verify(auditService).logAction(org.mockito.ArgumentMatchers.eq("usr-1 (root)"),
                org.mockito.ArgumentMatchers.eq("USER_ROLE_CHANGED"),
                org.mockito.ArgumentMatchers.eq("usr-5"), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("a missing user yields 404 semantics")
    void missingUserThrows() {
        when(userRepository.findById("usr-nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.updateUserRole("usr-nope", Map.of("role", "ADMIN")))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("deactivating a user revokes local access")
    void deactivatesUser() {
        when(userRepository.findById("usr-5")).thenReturn(Optional.of(targetUser));

        var response = controller.updateUserStatus("usr-5", Map.of("active", false));

        assertThat(response.getBody().isActive()).isFalse();
        verify(auditService).logAction(org.mockito.ArgumentMatchers.eq("usr-1 (root)"),
                org.mockito.ArgumentMatchers.eq("USER_DEACTIVATED"),
                org.mockito.ArgumentMatchers.eq("usr-5"), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("lists roles with their permissions")
    void listsRoles() {
        when(rolePermissionRepository.findByRole(Role.VIEWER)).thenReturn(List.of(
                RolePermissionEntity.builder().id("rp-1").role(Role.VIEWER)
                        .resourceType(ResourceType.SERVICE).action(PermissionAction.READ).build()));

        var body = controller.listRoles().getBody();

        assertThat(body.keySet()).containsExactlyInAnyOrder("VIEWER", "DEVELOPER", "TECH_LEAD", "ADMIN");
        @SuppressWarnings("unchecked")
        var viewer = (Map<String, Object>) body.get("VIEWER");
        assertThat(viewer.get("rank")).isEqualTo(0);
    }

    @Test
    @DisplayName("granting an existing permission is refused")
    void duplicateGrantRefused() {
        when(rolePermissionRepository.existsByRoleAndResourceTypeAndAction(Role.DEVELOPER,
                ResourceType.SERVICE, PermissionAction.READ)).thenReturn(true);

        assertThatThrownBy(() -> controller.grantPermission("developer",
                Map.of("resourceType", "SERVICE", "action", "READ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already holds");
    }

    @Test
    @DisplayName("revoking a permission returns 204")
    void revokesPermission() {
        var response = controller.revokePermission("developer", "SERVICE", "READ");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(rolePermissionRepository).deleteByRoleAndResourceTypeAndAction(Role.DEVELOPER,
                ResourceType.SERVICE, PermissionAction.READ);
        verify(policyDecisionService).invalidatePolicyCache();
    }

    @Test
    @DisplayName("creates an ABAC policy and invalidates the cache")
    void createsPolicy() {
        AbacPolicyEntity policy = AbacPolicyEntity.builder()
                .id("pol-x").name("Team scoping").resourceType("SERVICE").action("UPDATE")
                .effect(PolicyEffect.DENY).build();
        when(abacPolicyRepository.save(org.mockito.ArgumentMatchers.any(AbacPolicyEntity.class)))
                .thenReturn(policy);

        var response = controller.createPolicy(policy);

        assertThat(response.getBody()).isEqualTo(policy);
        verify(policyDecisionService).invalidatePolicyCache();
    }

    @Test
    @DisplayName("simulate dry-runs a decision with real resource attributes")
    void simulatesDecision() {
        UserEntity subject = UserEntity.builder()
                .id("usr-5").username("eve").role(Role.DEVELOPER).team("Equipe Paiement").build();
        when(userRepository.findById("usr-5")).thenReturn(Optional.of(subject));
        when(resourceAttributeResolver.resolve(ResourceType.FEATURE_FLAG, "ff-1", 80))
                .thenReturn(com.idp.security.AccessContext.builder()
                        .resourceId("ff-1").ownerTeam("Equipe Paiement").requestedRolloutPercent(80).build());
        when(policyDecisionService.evaluate(org.mockito.ArgumentMatchers.any(AuthenticatedUser.class),
                org.mockito.ArgumentMatchers.eq(ResourceType.FEATURE_FLAG),
                org.mockito.ArgumentMatchers.eq(PermissionAction.ROLLOUT),
                org.mockito.ArgumentMatchers.any(com.idp.security.AccessContext.class)))
                .thenReturn(AccessDecision.deny(AccessDecision.Stage.ABAC, ResourceType.FEATURE_FLAG,
                        PermissionAction.ROLLOUT, "ceiling", "pol-30"));

        var body = controller.simulate(Map.of(
                "userId", "usr-5",
                "resourceType", "FEATURE_FLAG",
                "action", "ROLLOUT",
                "resourceId", "ff-1",
                "rolloutPercent", 80)).getBody();

        assertThat(body.get("granted")).isEqualTo(false);
        assertThat(body.get("stage").toString()).isEqualTo("ABAC");
        assertThat(body.get("policyId")).isEqualTo("pol-30");
    }
}
