package com.idp.security;

import com.idp.domain.PermissionAction;
import com.idp.domain.ResourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Named helper exposed to method-security expressions as {@code @authz}, for checks
 * that carry a request parameter {@code hasPermission} cannot express — chiefly the
 * canary rollout ceiling, which depends on the percentage being requested.
 *
 * <p>Also usable imperatively from service code for row-level filtering.
 */
@Component("authz")
@RequiredArgsConstructor
public class AuthorizationGuard {

    private final PolicyDecisionService policyDecisionService;
    private final ResourceAttributeResolver resourceAttributeResolver;

    /**
     * Rollout is bounded by ABAC: a DEVELOPER may advance to 50%, a TECH_LEAD to 100%.
     * The requested percentage is part of the decision, so it must be supplied here.
     */
    public boolean canRollout(String flagId, Integer requestedPercent) {
        AccessContext context = resourceAttributeResolver.resolve(
                ResourceType.FEATURE_FLAG, flagId, requestedPercent);
        return decide(ResourceType.FEATURE_FLAG, PermissionAction.ROLLOUT, context);
    }

    /** Generic entry point for imperative checks inside service code. */
    public boolean can(ResourceType type, PermissionAction action, String resourceId) {
        AccessContext context = resourceAttributeResolver.resolve(type, resourceId, null);
        return decide(type, action, context);
    }

    /**
     * Row-level visibility test used when filtering collections. Reuses the pure
     * evaluation path so that filtering out N rows does not emit N denial audit
     * entries — only an explicit refusal is auditable.
     */
    public boolean canSee(ResourceType type, PermissionAction action, String ownerTeam) {
        AuthenticatedUser actor = CurrentUser.get().orElse(null);
        if (actor == null) {
            return false;
        }
        AccessContext context = AccessContext.builder().ownerTeam(ownerTeam).build();
        return policyDecisionService.evaluate(actor, type, action, context).isGranted();
    }

    private boolean decide(ResourceType type, PermissionAction action, AccessContext context) {
        AuthenticatedUser actor = CurrentUser.get().orElse(null);
        return policyDecisionService.decide(actor, type, action, context).isGranted();
    }
}
