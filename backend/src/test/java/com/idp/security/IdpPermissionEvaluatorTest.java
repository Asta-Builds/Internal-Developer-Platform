package com.idp.security;

import com.idp.domain.PermissionAction;
import com.idp.domain.ResourceType;
import com.idp.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Backs {@code @PreAuthorize("hasPermission(...)")}. Everything routes through the
 * policy engine; malformed expressions are refused rather than guessed at.
 */
@ExtendWith(MockitoExtension.class)
class IdpPermissionEvaluatorTest {

    @Mock private PolicyDecisionService policyDecisionService;
    @Mock private ResourceAttributeResolver resourceAttributeResolver;

    private IdpPermissionEvaluator evaluator;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        evaluator = new IdpPermissionEvaluator(policyDecisionService, resourceAttributeResolver);
        authentication = new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder()
                        .id("usr-1").username("alice").role(Role.ADMIN).team("Platform").build(),
                null, List.of());
    }

    @Test
    @DisplayName("the two-argument form is always refused")
    void twoArgumentFormRefused() {
        assertThat(evaluator.hasPermission(authentication, "srv-payment", "DELETE")).isFalse();
    }

    @Test
    @DisplayName("malformed resource types or permissions are refused")
    void malformedExpressionsRefused() {
        assertThat(evaluator.hasPermission(authentication, "srv-payment", "NOT_A_TYPE", "DELETE")).isFalse();
        assertThat(evaluator.hasPermission(authentication, "srv-payment", "SERVICE", "NOT_AN_ACTION")).isFalse();
    }

    @Test
    @DisplayName("a granted decision passes the expression")
    void grantedDecisionPasses() {
        when(resourceAttributeResolver.resolve(eq(ResourceType.SERVICE), eq("srv-payment"), isNull()))
                .thenReturn(AccessContext.builder().resourceId("srv-payment").build());
        when(policyDecisionService.decide(any(AuthenticatedUser.class),
                eq(ResourceType.SERVICE), eq(PermissionAction.DELETE), any(AccessContext.class)))
                .thenReturn(AccessDecision.allow(ResourceType.SERVICE, PermissionAction.DELETE));

        assertThat(evaluator.hasPermission(authentication, "srv-payment", "SERVICE", "delete")).isTrue();
    }

    @Test
    @DisplayName("a denied decision fails the expression")
    void deniedDecisionFails() {
        when(resourceAttributeResolver.resolve(eq(ResourceType.SERVICE), eq("srv-payment"), isNull()))
                .thenReturn(AccessContext.builder().resourceId("srv-payment").build());
        when(policyDecisionService.decide(any(AuthenticatedUser.class),
                eq(ResourceType.SERVICE), eq(PermissionAction.DELETE), any(AccessContext.class)))
                .thenReturn(AccessDecision.deny(AccessDecision.Stage.RBAC, ResourceType.SERVICE,
                        PermissionAction.DELETE, "no grant", null));

        assertThat(evaluator.hasPermission(authentication, "srv-payment", "SERVICE", "DELETE")).isFalse();
    }

    @Test
    @DisplayName("null target ids pass through as collection-level actions")
    void nullTargetIdPassesThrough() {
        when(resourceAttributeResolver.resolve(eq(ResourceType.SERVICE), isNull(), isNull()))
                .thenReturn(AccessContext.empty());
        when(policyDecisionService.decide(any(AuthenticatedUser.class),
                eq(ResourceType.SERVICE), eq(PermissionAction.READ), any(AccessContext.class)))
                .thenReturn(AccessDecision.allow(ResourceType.SERVICE, PermissionAction.READ));

        assertThat(evaluator.hasPermission(authentication, null, "SERVICE", "READ")).isTrue();
        verify(policyDecisionService).decide(any(AuthenticatedUser.class),
                eq(ResourceType.SERVICE), eq(PermissionAction.READ), any(AccessContext.class));
    }
}
