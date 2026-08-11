package com.idp.security;

import com.idp.domain.Role;
import com.idp.domain.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reconciled principal every authorization decision reads role and team from.
 * Locks in the drift semantics: the token-claimed role is retained for reporting
 * only and never equals the applied authority.
 */
class AuthenticatedUserTest {

    private UserEntity user() {
        return UserEntity.builder()
                .id("usr-7")
                .username("carol")
                .email("carol@company.internal")
                .role(Role.DEVELOPER)
                .team("Equipe Paiement")
                .keycloakSubject("kc-sub-7")
                .active(true)
                .build();
    }

    @Test
    @DisplayName("is built from the internal user record")
    void fromUserEntity() {
        AuthenticatedUser actor = AuthenticatedUser.from(user(), Role.DEVELOPER);

        assertThat(actor.getId()).isEqualTo("usr-7");
        assertThat(actor.getUsername()).isEqualTo("carol");
        assertThat(actor.getEmail()).isEqualTo("carol@company.internal");
        assertThat(actor.getRole()).isEqualTo(Role.DEVELOPER);
        assertThat(actor.getTeam()).isEqualTo("Equipe Paiement");
        assertThat(actor.getKeycloakSubject()).isEqualTo("kc-sub-7");
        assertThat(actor.getClaimedRole()).isEqualTo(Role.DEVELOPER);
    }

    @Test
    @DisplayName("reports drift when the IdP asserted a different role")
    void detectsRoleDrift() {
        AuthenticatedUser drifter = AuthenticatedUser.from(user(), Role.ADMIN);
        assertThat(drifter.hasRoleDrift()).isTrue();

        AuthenticatedUser aligned = AuthenticatedUser.from(user(), Role.DEVELOPER);
        assertThat(aligned.hasRoleDrift()).isFalse();

        AuthenticatedUser noClaim = AuthenticatedUser.from(user(), null);
        assertThat(noClaim.hasRoleDrift()).isFalse();
    }

    @Test
    @DisplayName("renders an audit-friendly actor label")
    void actorLabel() {
        assertThat(AuthenticatedUser.from(user(), null).toActorId())
                .isEqualTo("usr-7 (carol)");
    }
}
