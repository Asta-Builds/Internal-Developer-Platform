package com.idp.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * A contextual (attribute-based) rule layered on top of the RBAC grant.
 *
 * <p>Condition columns are AND-ed; a {@code null} (or {@code false} for the boolean
 * flags) means the policy does not constrain that attribute. {@code resourceType} and
 * {@code action} accept the literal {@code *} wildcard via {@link #matchesTarget}.
 *
 * <p>Stored in the database and editable by ADMIN so the policy set can change
 * without a redeploy — and, critically, without any of it living in Keycloak.
 */
@Entity
@Table(name = "abac_policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbacPolicyEntity {

    public static final String WILDCARD = "*";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    private String description;

    /** A {@link ResourceType} name, or {@code *}. */
    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    /** A {@link PermissionAction} name, or {@code *}. */
    @Column(nullable = false)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PolicyEffect effect;

    /** Requires the actor's team to equal the resource's owning team. */
    @Column(name = "require_same_team", nullable = false)
    @Builder.Default
    private boolean requireSameTeam = false;

    /**
     * Role at or above which this policy no longer applies — the escape hatch that
     * turns "developers are team-scoped" into "…but leads are not".
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "min_role")
    private Role minRole;

    /** Applies only when the target resource sits in this environment. */
    private String environment;

    /** Applies only when the target resource carries this criticality. */
    private String criticality;

    /** Applies only when the requested rollout exceeds this ceiling. */
    @Column(name = "max_rollout_percent")
    private Integer maxRolloutPercent;

    /** Requires the request to originate from a configured corporate IP range. */
    @Column(name = "require_corporate_ip", nullable = false)
    @Builder.Default
    private boolean requireCorporateIp = false;

    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 100;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /** True when this policy governs the given resource/action pair. */
    public boolean matchesTarget(ResourceType type, PermissionAction requested) {
        boolean typeMatches = WILDCARD.equals(resourceType) || type.name().equals(resourceType);
        boolean actionMatches = WILDCARD.equals(action) || requested.name().equals(action);
        return typeMatches && actionMatches;
    }
}
