package com.idp.security;

import com.idp.domain.Role;
import com.idp.domain.UserEntity;
import com.idp.repository.UserRepository;
import com.idp.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns a verified Keycloak token into the platform's own notion of a user.
 *
 * <p>This is the seam that keeps AuthN and AuthZ separate. The token proves identity;
 * everything that governs permissions — role, team, active status — is read from the
 * {@code users} table. If the token asserts {@code ADMIN} but the row says
 * {@code DEVELOPER}, the row wins and the divergence is audited.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InternalUserReconciliationService {

    private final UserRepository userRepository;
    private final AuditService auditService;

    /**
     * Role assigned to a first-time login with no pre-existing internal record.
     * Least privilege by default; an ADMIN promotes them afterwards.
     */
    @Value("${idp.security.default-role-on-provision:VIEWER}")
    private String defaultRoleOnProvision;

    /**
     * When false (the default), a token for an unknown principal is rejected instead
     * of auto-provisioning a VIEWER record. Enable only for local development.
     */
    @Value("${idp.security.auto-provision-unknown-users:false}")
    private boolean autoProvisionUnknownUsers;

    /**
     * @return the reconciled internal user, or empty when the subject maps to no
     *         active internal account and must therefore be refused.
     */
    @Transactional
    public Optional<AuthenticatedUser> reconcile(Jwt jwt) {
        String subject = jwt.getSubject();
        String username = firstNonBlank(
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("email"),
                subject);

        if (username == null) {
            log.warn("[AUTHN] Rejecting token with neither a username nor a subject claim");
            return Optional.empty();
        }

        // The role the IdP believes the user has. Retained for drift reporting only —
        // it never feeds an authorization decision.
        Role claimedRole = extractClaimedRole(jwt);

        Optional<UserEntity> existing = userRepository.findByKeycloakSubject(subject);
        if (existing.isEmpty()) {
            existing = userRepository.findByUsername(username);
            // Bind the subject on first login so later lookups are stable across renames.
            existing.ifPresent(user -> {
                user.setKeycloakSubject(subject);
                userRepository.save(user);
                log.info("[AUTHN] Bound Keycloak subject {} to internal user {}", subject, user.getId());
            });
        }

        UserEntity user = existing.orElse(null);
        if (user == null) {
            if (!autoProvisionUnknownUsers) {
                log.warn("[AUTHN] Rejecting '{}' — no internal user record and auto-provisioning is disabled", username);
                auditService.logAction(subject, "ACCESS_DENIED_UNKNOWN_PRINCIPAL", username,
                        "Valid Keycloak token presented by a principal with no internal user record");
                return Optional.empty();
            }
            user = provision(subject, username, jwt.getClaimAsString("email"), claimedRole);
        }

        if (!user.isActive()) {
            log.warn("[AUTHN] Rejecting deactivated internal user '{}'", username);
            auditService.logAction(user.getId() + " (" + username + ")", "ACCESS_DENIED_INACTIVE_USER",
                    user.getId(), "Deactivated user presented a valid Keycloak token");
            return Optional.empty();
        }

        AuthenticatedUser principal = AuthenticatedUser.from(user, claimedRole);
        if (principal.hasRoleDrift()) {
            log.info("[AUTHZ] Role drift for '{}': token claims {}, platform holds {}. Platform value applied.",
                    username, claimedRole, user.getRole());
        }
        return Optional.of(principal);
    }

    /**
     * Always provisions at {@link #defaultRoleOnProvision}, never at the role the token
     * asserts — honouring a claim here would let the IdP mint its own privileges, which
     * is the one coupling this class exists to prevent. {@code claimedRole} is recorded
     * in the audit entry so the gap between what Keycloak asserted and what the platform
     * granted is visible from the first login onwards.
     */
    private UserEntity provision(String subject, String username, String email, Role claimedRole) {
        Role role = Role.valueOf(defaultRoleOnProvision);
        UserEntity user = UserEntity.builder()
                .id("usr-" + UUID.randomUUID().toString().substring(0, 8))
                .username(username)
                .email(email != null ? email : username + "@company.internal")
                .role(role)
                .keycloakSubject(subject)
                .active(true)
                .build();

        userRepository.save(user);
        auditService.logAction(user.getId() + " (" + username + ")", "USER_PROVISIONED", user.getId(),
                "First login via Keycloak; provisioned with least-privilege role " + role.name()
                        + "; token asserted " + (claimedRole != null ? claimedRole.name() : "no role"));
        log.info("[AUTHN] Provisioned internal user {} with least-privilege role {} for Keycloak subject {} (token asserted {})",
                user.getId(), role, subject, claimedRole);
        return user;
    }

    /**
     * Reads {@code realm_access.roles} purely to detect drift. Deliberately not used
     * to grant anything.
     */
    private Role extractClaimedRole(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return null;
        }
        // Highest-ranked group the IdP asserts.
        return roles.stream()
                .map(String::valueOf)
                .map(Role::fromExternalGroup)
                .max(Comparator.comparingInt(Role::getRank))
                .orElse(null);
    }

    // Arrays.stream, not List.of: the claims may legitimately be null.
    private String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        return Arrays.stream(candidates)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }
}
