package com.idp.security;

import lombok.Builder;
import lombok.Getter;

/**
 * The resource-side attributes an ABAC policy evaluates against. Resolved from the
 * database by {@link ResourceAttributeResolver} — never trusted from the caller,
 * which is what made the previous {@code /enterprise/abac/eval} endpoint meaningless.
 */
@Getter
@Builder
public class AccessContext {

    /** Identifier of the resource being acted upon; null for collection-level actions. */
    private final String resourceId;

    /** Owning team of the target resource, if it has one. */
    private final String ownerTeam;

    /** Deployment environment of the target resource, e.g. PROD. */
    private final String environment;

    /** STANDARD or TIER_1. */
    private final String criticality;

    /** For rollout actions, the percentage being requested. */
    private final Integer requestedRolloutPercent;

    /** Remote address the request arrived from. */
    private final String clientIp;

    public static AccessContext empty() {
        return AccessContext.builder().build();
    }
}
