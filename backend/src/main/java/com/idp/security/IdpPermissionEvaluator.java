package com.idp.security;

import com.idp.domain.PermissionAction;
import com.idp.domain.ResourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * Backs {@code @PreAuthorize("hasPermission(#id, 'SERVICE', 'DELETE')")}.
 *
 * <p>Every expression routes through {@link PolicyDecisionService}, so no controller
 * ever tests a raw Keycloak role. Pass a {@code null} target id for collection-level
 * actions such as listing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdpPermissionEvaluator implements PermissionEvaluator {

    private final PolicyDecisionService policyDecisionService;
    private final ResourceAttributeResolver resourceAttributeResolver;

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        // The two-argument form carries no resource type, so it cannot be evaluated
        // against the matrix. Refuse rather than guess.
        log.warn("[AUTHZ] Unsupported two-argument hasPermission call for permission '{}'; denying", permission);
        return false;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId,
                                 String targetType, Object permission) {

        ResourceType resourceType = parseEnum(ResourceType.class, targetType);
        PermissionAction action = parseEnum(PermissionAction.class, String.valueOf(permission));
        if (resourceType == null || action == null) {
            log.warn("[AUTHZ] Denying malformed permission expression: type='{}', permission='{}'",
                    targetType, permission);
            return false;
        }

        AuthenticatedUser actor = CurrentUser.from(authentication).orElse(null);
        String resourceId = targetId != null ? String.valueOf(targetId) : null;

        AccessContext context = resourceAttributeResolver.resolve(resourceType, resourceId, null);
        return policyDecisionService.decide(actor, resourceType, action, context).isGranted();
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
