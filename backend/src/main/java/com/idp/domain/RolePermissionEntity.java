package com.idp.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * One cell of the RBAC matrix: a role may perform an action on a resource family.
 * Editable from the admin UI; this table — never the Keycloak role set — is the
 * coarse-grained authority on what a role can do.
 */
@Entity
@Table(name = "role_permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RolePermissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false)
    private ResourceType resourceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PermissionAction action;

    private String description;
}
