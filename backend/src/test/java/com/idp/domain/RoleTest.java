package com.idp.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The role hierarchy drives every {@code minRole} exemption in the ABAC layer, and the
 * external-group mapping is the only place an identity-provider value influences the
 * platform's own role model.
 */
class RoleTest {

    @Test
    @DisplayName("ranks roles from VIEWER up to ADMIN")
    void hierarchyOrder() {
        assertThat(Role.ADMIN.isAtLeast(Role.TECH_LEAD)).isTrue();
        assertThat(Role.TECH_LEAD.isAtLeast(Role.DEVELOPER)).isTrue();
        assertThat(Role.DEVELOPER.isAtLeast(Role.VIEWER)).isTrue();
        assertThat(Role.VIEWER.isAtLeast(Role.DEVELOPER)).isFalse();
        assertThat(Role.DEVELOPER.isAtLeast(Role.DEVELOPER)).isTrue();
    }

    @Test
    @DisplayName("maps the legacy seeded group names onto canonical roles")
    void mapsLegacyGroups() {
        // V3 seeded these two; they must land on real roles rather than blow up.
        assertThat(Role.fromExternalGroup("DEVOPS_ENGINEER")).isEqualTo(Role.TECH_LEAD);
        assertThat(Role.fromExternalGroup("SECURITY_LEAD")).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("normalises prefixes, case and separators")
    void normalisesInput() {
        assertThat(Role.fromExternalGroup("ROLE_tech-lead")).isEqualTo(Role.TECH_LEAD);
        assertThat(Role.fromExternalGroup("  admin  ")).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("falls back to the least privileged role for unknown or absent groups")
    void failsClosedOnUnknownGroup() {
        assertThat(Role.fromExternalGroup("cluster-superuser")).isEqualTo(Role.VIEWER);
        assertThat(Role.fromExternalGroup(null)).isEqualTo(Role.VIEWER);
    }
}
