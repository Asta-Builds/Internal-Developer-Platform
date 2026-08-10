package com.idp.domain;

/**
 * The four canonical platform roles. Mapped <em>from</em> Keycloak groups on login,
 * but the value persisted on {@link UserEntity} is the authority for every
 * authorization decision — never the raw JWT claim.
 */
public enum Role {

    VIEWER(0),
    DEVELOPER(1),
    TECH_LEAD(2),
    ADMIN(3);

    private final int rank;

    Role(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }

    /** True when this role sits at or above {@code other} in the hierarchy. */
    public boolean isAtLeast(Role other) {
        return other != null && this.rank >= other.rank;
    }

    /**
     * Best-effort mapping of an external identity-provider group name onto a platform
     * role. Unknown groups deliberately fall back to the least privileged role rather
     * than failing open.
     */
    public static Role fromExternalGroup(String group) {
        if (group == null) {
            return VIEWER;
        }
        String normalized = group.trim().toUpperCase().replace('-', '_');
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring("ROLE_".length());
        }
        return switch (normalized) {
            case "ADMIN", "PLATFORM_ADMIN", "SECURITY_LEAD" -> ADMIN;
            case "TECH_LEAD", "LEAD", "DEVOPS_ENGINEER" -> TECH_LEAD;
            case "DEVELOPER", "DEV", "ENGINEER" -> DEVELOPER;
            default -> VIEWER;
        };
    }
}
