package com.idp.security;

import com.idp.domain.*;
import com.idp.repository.AbacPolicyRepository;
import com.idp.repository.RolePermissionRepository;
import com.idp.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * The platform's authorization engine — the single place where an access decision is
 * made. Keycloak is never consulted here; it only established <em>who</em> the caller
 * is, upstream of this class.
 *
 * <p>Evaluation runs in two stages and is deny-by-default:
 * <ol>
 *   <li><b>RBAC</b> — the {@code role_permissions} matrix must grant
 *       (role, resourceType, action), otherwise the request is refused outright.</li>
 *   <li><b>ABAC</b> — every enabled policy matching the resource/action pair is
 *       evaluated in priority order. A policy whose conditions all hold contributes
 *       its effect, and a matching DENY is final.</li>
 * </ol>
 *
 * <p>Every denial is written to {@link AuditService} so refusals are traceable.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyDecisionService {

    private final RolePermissionRepository rolePermissionRepository;
    private final AbacPolicyRepository abacPolicyRepository;
    private final CorporateNetworkService corporateNetworkService;
    private final AuditService auditService;

    /**
     * Evaluates a request and records the outcome. Returns the decision rather than
     * throwing, so callers can distinguish "refuse" from "filter out".
     */
    public AccessDecision decide(AuthenticatedUser actor,
                                 ResourceType resourceType,
                                 PermissionAction action,
                                 AccessContext context) {

        if (actor == null) {
            return AccessDecision.deny(AccessDecision.Stage.AUTHENTICATION, resourceType, action,
                    "No authenticated internal user bound to this request", null);
        }

        AccessDecision decision = evaluate(actor, resourceType, action, context);

        if (!decision.isGranted()) {
            auditDenial(actor, decision, context);
        }
        return decision;
    }

    /** Pure evaluation with no audit side effect — used by the ADMIN policy simulator. */
    public AccessDecision evaluate(AuthenticatedUser actor,
                                   ResourceType resourceType,
                                   PermissionAction action,
                                   AccessContext context) {

        // Stage 1 — RBAC. Absent an explicit grant, nothing else can rescue the request.
        if (!hasRbacGrant(actor.getRole(), resourceType, action)) {
            return AccessDecision.deny(AccessDecision.Stage.RBAC, resourceType, action,
                    "Role %s holds no %s permission on %s".formatted(actor.getRole(), action, resourceType),
                    null);
        }

        // Stage 2 — ABAC. Contextual rules may withdraw a grant the matrix allowed.
        AccessContext effectiveContext = context != null ? context : AccessContext.empty();
        for (AbacPolicyEntity policy : loadActivePolicies()) {
            if (!policy.matchesTarget(resourceType, action)) {
                continue;
            }
            if (!conditionsHold(policy, actor, effectiveContext)) {
                continue;
            }
            if (policy.getEffect() == PolicyEffect.DENY) {
                return AccessDecision.deny(AccessDecision.Stage.ABAC, resourceType, action,
                        "Denied by policy '%s': %s".formatted(policy.getName(), policy.getDescription()),
                        policy.getId());
            }
        }

        return AccessDecision.allow(resourceType, action);
    }

    /**
     * True when every condition a policy declares is satisfied by this request, which
     * is what makes the policy applicable. Unset conditions are skipped.
     */
    private boolean conditionsHold(AbacPolicyEntity policy, AuthenticatedUser actor, AccessContext ctx) {

        // minRole is an exemption, not a requirement: a sufficiently senior actor
        // escapes the policy entirely.
        if (policy.getMinRole() != null && actor.getRole().isAtLeast(policy.getMinRole())) {
            return false;
        }

        if (policy.isRequireSameTeam()) {
            // Only applicable once the resource actually declares an owner.
            if (ctx.getOwnerTeam() == null) {
                return false;
            }
            if (Objects.equals(ctx.getOwnerTeam(), actor.getTeam())) {
                return false; // Same team, so the restriction does not bite.
            }
        }

        if (policy.getEnvironment() != null
                && !policy.getEnvironment().equalsIgnoreCase(ctx.getEnvironment())) {
            return false;
        }

        if (policy.getCriticality() != null
                && !policy.getCriticality().equalsIgnoreCase(ctx.getCriticality())) {
            return false;
        }

        if (policy.getMaxRolloutPercent() != null) {
            Integer requested = ctx.getRequestedRolloutPercent();
            if (requested == null || requested <= policy.getMaxRolloutPercent()) {
                return false; // Within the ceiling, so the policy does not apply.
            }
        }

        if (policy.isRequireCorporateIp()
                && corporateNetworkService.isCorporateAddress(ctx.getClientIp())) {
            return false; // Already on the corporate network.
        }

        return true;
    }

    @Cacheable(value = "rbac_matrix", key = "#role.name() + ':' + #resourceType.name() + ':' + #action.name()")
    public boolean hasRbacGrant(Role role, ResourceType resourceType, PermissionAction action) {
        return rolePermissionRepository.existsByRoleAndResourceTypeAndAction(role, resourceType, action);
    }

    private List<AbacPolicyEntity> loadActivePolicies() {
        return abacPolicyRepository.findByEnabledTrueOrderByPriorityAsc();
    }

    /** Clears the cached matrix after an admin edits RBAC or ABAC configuration. */
    @CacheEvict(value = "rbac_matrix", allEntries = true)
    public void invalidatePolicyCache() {
        log.info("[AUTHZ] RBAC/ABAC decision cache invalidated after a policy change");
    }

    private void auditDenial(AuthenticatedUser actor, AccessDecision decision, AccessContext context) {
        String target = context != null && context.getResourceId() != null
                ? context.getResourceId()
                : decision.getResourceType().name();

        auditService.logAction(
                actor.toActorId(),
                decision.toAuditAction(),
                target,
                "[%s] %s".formatted(decision.getStage(), decision.getReason()));

        log.warn("[AUTHZ] DENIED {} {} on {} for {} — {}",
                decision.getAction(), decision.getResourceType(), target,
                actor.getUsername(), decision.getReason());
    }
}
