package com.idp.security;

import com.idp.domain.*;
import com.idp.repository.AbacPolicyRepository;
import com.idp.repository.RolePermissionRepository;
import com.idp.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Behavioural tests for the platform's authorization engine.
 *
 * <p>These lock in the rule that matters architecturally: the decision is computed
 * from the internal matrix and policy tables, and a Keycloak-asserted role has no
 * bearing on the outcome.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PolicyDecisionServiceTest {

    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private AbacPolicyRepository abacPolicyRepository;
    @Mock private CorporateNetworkService corporateNetworkService;
    @Mock private AuditService auditService;

    @InjectMocks private PolicyDecisionService policyDecisionService;

    private AuthenticatedUser developer;
    private AuthenticatedUser techLead;
    private AuthenticatedUser admin;

    @BeforeEach
    void setUp() {
        developer = user("usr-3", "bob", Role.DEVELOPER, "Equipe Catalogue");
        techLead = user("usr-2", "alice", Role.TECH_LEAD, "Equipe Paiement");
        admin = user("usr-1", "admin", Role.ADMIN, "Platform Infrastructure");

        // Default: no ABAC policies unless a test installs some.
        when(abacPolicyRepository.findByEnabledTrueOrderByPriorityAsc()).thenReturn(List.of());
    }

    private AuthenticatedUser user(String id, String name, Role role, String team) {
        return AuthenticatedUser.builder()
                .id(id).username(name).email(name + "@company.internal")
                .role(role).team(team).build();
    }

    private void grant(Role role, ResourceType type, PermissionAction action) {
        when(rolePermissionRepository.existsByRoleAndResourceTypeAndAction(role, type, action))
                .thenReturn(true);
    }

    @Nested
    @DisplayName("RBAC stage")
    class RbacStage {

        @Test
        @DisplayName("denies when the matrix holds no grant for the role")
        void deniesWithoutGrant() {
            AccessDecision decision = policyDecisionService.decide(
                    developer, ResourceType.SERVICE, PermissionAction.DELETE, AccessContext.empty());

            assertThat(decision.isGranted()).isFalse();
            assertThat(decision.getStage()).isEqualTo(AccessDecision.Stage.RBAC);
        }

        @Test
        @DisplayName("allows when the matrix grants and no policy contradicts it")
        void allowsWithGrant() {
            grant(Role.DEVELOPER, ResourceType.SERVICE, PermissionAction.READ);

            AccessDecision decision = policyDecisionService.decide(
                    developer, ResourceType.SERVICE, PermissionAction.READ, AccessContext.empty());

            assertThat(decision.isGranted()).isTrue();
        }

        @Test
        @DisplayName("denies an unauthenticated caller before consulting the matrix")
        void deniesAnonymous() {
            AccessDecision decision = policyDecisionService.decide(
                    null, ResourceType.SERVICE, PermissionAction.READ, AccessContext.empty());

            assertThat(decision.isGranted()).isFalse();
            assertThat(decision.getStage()).isEqualTo(AccessDecision.Stage.AUTHENTICATION);
            verifyNoInteractions(rolePermissionRepository);
        }
    }

    @Nested
    @DisplayName("ABAC ownership")
    class Ownership {

        private AbacPolicyEntity sameTeamPolicy() {
            return AbacPolicyEntity.builder()
                    .id("pol-001").name("Own-team service mutation")
                    .resourceType("SERVICE").action("UPDATE")
                    .effect(PolicyEffect.DENY)
                    .requireSameTeam(true).minRole(Role.TECH_LEAD)
                    .priority(10).enabled(true)
                    .build();
        }

        @BeforeEach
        void installPolicy() {
            grant(Role.DEVELOPER, ResourceType.SERVICE, PermissionAction.UPDATE);
            grant(Role.TECH_LEAD, ResourceType.SERVICE, PermissionAction.UPDATE);
            when(abacPolicyRepository.findByEnabledTrueOrderByPriorityAsc())
                    .thenReturn(List.of(sameTeamPolicy()));
        }

        @Test
        @DisplayName("denies a developer mutating another team's service")
        void deniesForeignTeam() {
            AccessContext context = AccessContext.builder()
                    .resourceId("srv-payment").ownerTeam("Equipe Paiement").build();

            AccessDecision decision = policyDecisionService.decide(
                    developer, ResourceType.SERVICE, PermissionAction.UPDATE, context);

            assertThat(decision.isGranted()).isFalse();
            assertThat(decision.getStage()).isEqualTo(AccessDecision.Stage.ABAC);
            assertThat(decision.getPolicyId()).isEqualTo("pol-001");
        }

        @Test
        @DisplayName("allows a developer mutating their own team's service")
        void allowsOwnTeam() {
            AccessContext context = AccessContext.builder()
                    .resourceId("srv-catalog").ownerTeam("Equipe Catalogue").build();

            AccessDecision decision = policyDecisionService.decide(
                    developer, ResourceType.SERVICE, PermissionAction.UPDATE, context);

            assertThat(decision.isGranted()).isTrue();
        }

        @Test
        @DisplayName("exempts a TECH_LEAD from the own-team restriction via minRole")
        void techLeadExempt() {
            AccessContext context = AccessContext.builder()
                    .resourceId("srv-catalog").ownerTeam("Equipe Catalogue").build();

            AccessDecision decision = policyDecisionService.decide(
                    techLead, ResourceType.SERVICE, PermissionAction.UPDATE, context);

            assertThat(decision.isGranted()).isTrue();
        }
    }

    @Nested
    @DisplayName("ABAC rollout ceiling")
    class RolloutCeiling {

        @BeforeEach
        void installPolicy() {
            grant(Role.DEVELOPER, ResourceType.FEATURE_FLAG, PermissionAction.ROLLOUT);
            grant(Role.TECH_LEAD, ResourceType.FEATURE_FLAG, PermissionAction.ROLLOUT);
            when(abacPolicyRepository.findByEnabledTrueOrderByPriorityAsc()).thenReturn(List.of(
                    AbacPolicyEntity.builder()
                            .id("pol-030").name("Rollout above 50% requires TECH_LEAD")
                            .resourceType("FEATURE_FLAG").action("ROLLOUT")
                            .effect(PolicyEffect.DENY)
                            .minRole(Role.TECH_LEAD).maxRolloutPercent(50)
                            .priority(20).enabled(true)
                            .build()));
        }

        private AccessDecision rollout(AuthenticatedUser actor, int percent) {
            return policyDecisionService.decide(actor, ResourceType.FEATURE_FLAG, PermissionAction.ROLLOUT,
                    AccessContext.builder().resourceId("ff-1").requestedRolloutPercent(percent).build());
        }

        @Test
        @DisplayName("a developer may advance up to the 50% ceiling")
        void developerAtCeiling() {
            assertThat(rollout(developer, 50).isGranted()).isTrue();
        }

        @Test
        @DisplayName("a developer is refused one point above the ceiling")
        void developerAboveCeiling() {
            AccessDecision decision = rollout(developer, 51);
            assertThat(decision.isGranted()).isFalse();
            assertThat(decision.getPolicyId()).isEqualTo("pol-030");
        }

        @Test
        @DisplayName("a TECH_LEAD may go to 100%")
        void techLeadFullRollout() {
            assertThat(rollout(techLead, 100).isGranted()).isTrue();
        }
    }

    @Nested
    @DisplayName("ABAC criticality and network")
    class CriticalityAndNetwork {

        @Test
        @DisplayName("tier-1 deletion is refused below ADMIN and allowed for ADMIN")
        void tierOneDeletion() {
            grant(Role.TECH_LEAD, ResourceType.SERVICE, PermissionAction.DELETE);
            grant(Role.ADMIN, ResourceType.SERVICE, PermissionAction.DELETE);
            when(abacPolicyRepository.findByEnabledTrueOrderByPriorityAsc()).thenReturn(List.of(
                    AbacPolicyEntity.builder()
                            .id("pol-020").name("Tier-1 deletion is ADMIN only")
                            .resourceType("SERVICE").action("DELETE")
                            .effect(PolicyEffect.DENY)
                            .minRole(Role.ADMIN).criticality("TIER_1")
                            .priority(20).enabled(true)
                            .build()));

            AccessContext tierOne = AccessContext.builder()
                    .resourceId("srv-payment").criticality("TIER_1").build();

            assertThat(policyDecisionService.decide(techLead, ResourceType.SERVICE, PermissionAction.DELETE, tierOne)
                    .isGranted()).isFalse();
            assertThat(policyDecisionService.decide(admin, ResourceType.SERVICE, PermissionAction.DELETE, tierOne)
                    .isGranted()).isTrue();
        }

        @Test
        @DisplayName("deletion from outside the corporate network is refused")
        void nonCorporateAddressRefused() {
            grant(Role.TECH_LEAD, ResourceType.SERVICE, PermissionAction.DELETE);
            when(abacPolicyRepository.findByEnabledTrueOrderByPriorityAsc()).thenReturn(List.of(
                    AbacPolicyEntity.builder()
                            .id("pol-021").name("Service deletion from corporate network")
                            .resourceType("SERVICE").action("DELETE")
                            .effect(PolicyEffect.DENY)
                            .requireCorporateIp(true)
                            .priority(30).enabled(true)
                            .build()));

            when(corporateNetworkService.isCorporateAddress("203.0.113.7")).thenReturn(false);
            when(corporateNetworkService.isCorporateAddress("10.1.2.3")).thenReturn(true);

            AccessContext external = AccessContext.builder()
                    .resourceId("srv-catalog").clientIp("203.0.113.7").build();
            AccessContext internal = AccessContext.builder()
                    .resourceId("srv-catalog").clientIp("10.1.2.3").build();

            assertThat(policyDecisionService.decide(techLead, ResourceType.SERVICE, PermissionAction.DELETE, external)
                    .isGranted()).isFalse();
            assertThat(policyDecisionService.decide(techLead, ResourceType.SERVICE, PermissionAction.DELETE, internal)
                    .isGranted()).isTrue();
        }
    }

    @Nested
    @DisplayName("Auditing and identity")
    class AuditingAndIdentity {

        @Test
        @DisplayName("writes an audit entry on refusal")
        void auditsDenial() {
            policyDecisionService.decide(developer, ResourceType.ADMIN, PermissionAction.MANAGE,
                    AccessContext.builder().resourceId("usr-1").build());

            verify(auditService).logAction(
                    eq("usr-3 (bob)"),
                    eq("ACCESS_DENIED_ADMIN_MANAGE"),
                    eq("usr-1"),
                    anyString());
        }

        @Test
        @DisplayName("does not audit a granted request")
        void doesNotAuditGrant() {
            grant(Role.DEVELOPER, ResourceType.SERVICE, PermissionAction.READ);

            policyDecisionService.decide(developer, ResourceType.SERVICE, PermissionAction.READ,
                    AccessContext.empty());

            verify(auditService, never()).logAction(anyString(), anyString(), any(), any());
        }

        @Test
        @DisplayName("ignores the role asserted by Keycloak and uses the stored role")
        void keycloakClaimIsNotAuthoritative() {
            // The token claims ADMIN; the internal record says DEVELOPER.
            AuthenticatedUser impersonator = AuthenticatedUser.builder()
                    .id("usr-3").username("bob").email("bob@company.internal")
                    .role(Role.DEVELOPER).team("Equipe Catalogue")
                    .claimedRole(Role.ADMIN)
                    .build();

            grant(Role.ADMIN, ResourceType.ADMIN, PermissionAction.MANAGE);

            AccessDecision decision = policyDecisionService.decide(
                    impersonator, ResourceType.ADMIN, PermissionAction.MANAGE, AccessContext.empty());

            assertThat(decision.isGranted()).isFalse();
            assertThat(impersonator.hasRoleDrift()).isTrue();
        }

        @Test
        @DisplayName("evaluate() is side-effect free so the simulator writes no audit rows")
        void evaluateDoesNotAudit() {
            policyDecisionService.evaluate(developer, ResourceType.ADMIN, PermissionAction.MANAGE,
                    AccessContext.empty());

            verifyNoInteractions(auditService);
        }
    }
}
