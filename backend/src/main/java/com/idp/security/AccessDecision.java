package com.idp.security;

import com.idp.domain.PermissionAction;
import com.idp.domain.ResourceType;
import lombok.Builder;
import lombok.Getter;

/**
 * The traceable outcome of one authorization evaluation. Carries the reason and the
 * deciding stage so that every denial can be written to the audit trail.
 */
@Getter
@Builder
public class AccessDecision {

    public enum Stage {
        /** No authenticated internal user. */
        AUTHENTICATION,
        /** The role/resource/action matrix. */
        RBAC,
        /** A contextual policy row. */
        ABAC
    }

    private final boolean granted;
    private final Stage stage;
    private final String reason;
    private final String policyId;
    private final ResourceType resourceType;
    private final PermissionAction action;

    public static AccessDecision allow(ResourceType type, PermissionAction action) {
        return AccessDecision.builder()
                .granted(true)
                .stage(Stage.ABAC)
                .reason("Granted by RBAC matrix with no contradicting ABAC policy")
                .resourceType(type)
                .action(action)
                .build();
    }

    public static AccessDecision deny(Stage stage, ResourceType type, PermissionAction action,
                                      String reason, String policyId) {
        return AccessDecision.builder()
                .granted(false)
                .stage(stage)
                .reason(reason)
                .policyId(policyId)
                .resourceType(type)
                .action(action)
                .build();
    }

    /** Compact label used as the audit {@code action}, e.g. {@code ACCESS_DENIED_SERVICE_DELETE}. */
    public String toAuditAction() {
        String verb = granted ? "ACCESS_GRANTED" : "ACCESS_DENIED";
        return verb + "_" + resourceType + "_" + action;
    }
}
