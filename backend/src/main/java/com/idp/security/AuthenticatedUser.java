package com.idp.security;

import com.idp.domain.Role;
import com.idp.domain.UserEntity;
import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;

/**
 * The authenticated actor as the platform understands them, built by reconciling a
 * verified Keycloak token against the internal {@code users} table.
 *
 * <p>The {@link #role} and {@link #team} carried here always come from the database,
 * never from a JWT claim.
 */
@Getter
@Builder
public class AuthenticatedUser implements Serializable {

    private final String id;
    private final String username;
    private final String email;
    private final Role role;
    private final String team;
    private final String keycloakSubject;

    /** The role advertised by the token, retained only for audit and drift reporting. */
    private final Role claimedRole;

    public static AuthenticatedUser from(UserEntity user, Role claimedRole) {
        return AuthenticatedUser.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .team(user.getTeam())
                .keycloakSubject(user.getKeycloakSubject())
                .claimedRole(claimedRole)
                .build();
    }

    /** True when the identity provider asserted a different role than the platform holds. */
    public boolean hasRoleDrift() {
        return claimedRole != null && claimedRole != role;
    }

    /** Audit-friendly actor label, e.g. {@code usr-2 (alice)}. */
    public String toActorId() {
        return id + " (" + username + ")";
    }
}
