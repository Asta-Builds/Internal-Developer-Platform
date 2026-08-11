package com.idp.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Target matching semantics of an ABAC policy row: literals or the {@code *}
 * wildcard, on both axes.
 */
class AbacPolicyEntityTest {

    private AbacPolicyEntity policy(String resourceType, String action) {
        return AbacPolicyEntity.builder()
                .id("pol-1").name("Test policy")
                .resourceType(resourceType).action(action)
                .effect(PolicyEffect.DENY)
                .build();
    }

    @Test
    @DisplayName("a literal pair matches only that pair")
    void literalMatch() {
        AbacPolicyEntity policy = policy("SERVICE", "DELETE");

        assertThat(policy.matchesTarget(ResourceType.SERVICE, PermissionAction.DELETE)).isTrue();
        assertThat(policy.matchesTarget(ResourceType.SERVICE, PermissionAction.UPDATE)).isFalse();
        assertThat(policy.matchesTarget(ResourceType.FEATURE_FLAG, PermissionAction.DELETE)).isFalse();
    }

    @Test
    @DisplayName("wildcards match any resource type or action")
    void wildcardMatch() {
        assertThat(policy(AbacPolicyEntity.WILDCARD, "DELETE")
                .matchesTarget(ResourceType.SERVICE, PermissionAction.DELETE)).isTrue();
        assertThat(policy(AbacPolicyEntity.WILDCARD, "DELETE")
                .matchesTarget(ResourceType.COPILOT, PermissionAction.DELETE)).isTrue();
        assertThat(policy("SERVICE", AbacPolicyEntity.WILDCARD)
                .matchesTarget(ResourceType.SERVICE, PermissionAction.READ)).isTrue();
        assertThat(policy(AbacPolicyEntity.WILDCARD, AbacPolicyEntity.WILDCARD)
                .matchesTarget(ResourceType.ADMIN, PermissionAction.MANAGE)).isTrue();
    }
}
