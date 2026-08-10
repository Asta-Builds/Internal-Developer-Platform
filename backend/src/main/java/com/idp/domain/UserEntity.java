package com.idp.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * The internal user record. This — not the JWT — is the source of truth for the
 * actor's role and team when an authorization decision is made.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /** Drives the {@code ownerTeam == user.team} ABAC rule. */
    private String team;

    /** The {@code sub} claim of the Keycloak token, bound on first successful login. */
    @Column(name = "keycloak_subject", unique = true)
    private String keycloakSubject;

    /**
     * Locally revocable access. A deactivated user is rejected even while holding a
     * valid, unexpired Keycloak token.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
