package com.idp.security;

import com.idp.domain.PermissionAction;
import com.idp.domain.ResourceType;
import com.idp.domain.Role;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The named {@code @authz} guard for request-parameter dependent checks.
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationGuardTest {

    @Mock private PolicyDecisionService policyDecisionService;
    @Mock private ResourceAttributeResolver resourceAttributeResolver;

    private AuthorizationGuard guard;

    @BeforeEach
    void setUp() {
        guard = new AuthorizationGuard(policyDecisionService, resourceAttributeResolver);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder()
                        .id("usr-2").username("bob").role(Role.DEVELOPER).team("Equipe Catalogue").build(),
                null, List.of()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("canRollout resolves the flag context with the requested percentage")
    void canRolloutResolvesContext() {
        when(resourceAttributeResolver.resolve(ResourceType.FEATURE_FLAG, "ff-1", 51))
                .thenReturn(AccessContext.builder().resourceId("ff-1").requestedRolloutPercent(51).build());
        when(policyDecisionService.decide(any(AuthenticatedUser.class),
                eq(ResourceType.FEATURE_FLAG), eq(PermissionAction.ROLLOUT), any(AccessContext.class)))
                .thenReturn(AccessDecision.allow(ResourceType.FEATURE_FLAG, PermissionAction.ROLLOUT));

        assertThat(guard.canRollout("ff-1", 51)).isTrue();
    }

    @Test
    @DisplayName("can delegates a generic resource/action check")
    void canDelegates() {
        when(resourceAttributeResolver.resolve(ResourceType.SERVICE, "srv-payment", null))
                .thenReturn(AccessContext.builder().resourceId("srv-payment").build());
        when(policyDecisionService.decide(any(AuthenticatedUser.class),
                eq(ResourceType.SERVICE), eq(PermissionAction.UPDATE), any(AccessContext.class)))
                .thenReturn(AccessDecision.deny(AccessDecision.Stage.ABAC, ResourceType.SERVICE,
                        PermissionAction.UPDATE, "own-team", "pol-1"));

        assertThat(guard.can(ResourceType.SERVICE, PermissionAction.UPDATE, "srv-payment")).isFalse();
    }

    @Test
    @DisplayName("canSee uses the side-effect free evaluation path")
    void canSeeUsesPureEvaluation() {
        when(policyDecisionService.evaluate(any(AuthenticatedUser.class),
                eq(ResourceType.SERVICE), eq(PermissionAction.READ), any(AccessContext.class)))
                .thenReturn(AccessDecision.allow(ResourceType.SERVICE, PermissionAction.READ));

        assertThat(guard.canSee(ResourceType.SERVICE, PermissionAction.READ, "Equipe Paiement")).isTrue();
        verify(policyDecisionService, never()).decide(any(), any(), any(), any());
    }

    @Test
    @DisplayName("all checks fail closed without a bound principal")
    void failsClosedWithoutPrincipal() {
        SecurityContextHolder.clearContext();

        assertThat(guard.canSee(ResourceType.SERVICE, PermissionAction.READ, "Equipe Paiement")).isFalse();
        verify(policyDecisionService, never()).evaluate(any(), any(), any(), any());
    }
}
