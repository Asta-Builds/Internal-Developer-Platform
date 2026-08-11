package com.idp.security;

import com.idp.domain.PermissionAction;
import com.idp.domain.ResourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The traceable outcome of one authorization evaluation.
 */
class AccessDecisionTest {

    @Test
    @DisplayName("allow carries the granted flag and a stable stage")
    void allowDecision() {
        AccessDecision decision = AccessDecision.allow(ResourceType.SERVICE, PermissionAction.READ);

        assertThat(decision.isGranted()).isTrue();
        assertThat(decision.getStage()).isEqualTo(AccessDecision.Stage.ABAC);
        assertThat(decision.getReason()).isNotBlank();
        assertThat(decision.getPolicyId()).isNull();
    }

    @Test
    @DisplayName("deny carries the deciding stage, reason and policy id")
    void denyDecision() {
        AccessDecision decision = AccessDecision.deny(AccessDecision.Stage.RBAC,
                ResourceType.SERVICE, PermissionAction.DELETE, "no grant", "pol-9");

        assertThat(decision.isGranted()).isFalse();
        assertThat(decision.getStage()).isEqualTo(AccessDecision.Stage.RBAC);
        assertThat(decision.getReason()).isEqualTo("no grant");
        assertThat(decision.getPolicyId()).isEqualTo("pol-9");
        assertThat(decision.getResourceType()).isEqualTo(ResourceType.SERVICE);
        assertThat(decision.getAction()).isEqualTo(PermissionAction.DELETE);
    }

    @Test
    @DisplayName("renders the compact audit action label")
    void auditActionLabel() {
        AccessDecision denied = AccessDecision.deny(AccessDecision.Stage.ABAC,
                ResourceType.FEATURE_FLAG, PermissionAction.ROLLOUT, "ceiling", null);
        assertThat(denied.toAuditAction()).isEqualTo("ACCESS_DENIED_FEATURE_FLAG_ROLLOUT");

        AccessDecision granted = AccessDecision.allow(ResourceType.AUDIT_LOG, PermissionAction.READ);
        assertThat(granted.toAuditAction()).isEqualTo("ACCESS_GRANTED_AUDIT_LOG_READ");
    }
}
