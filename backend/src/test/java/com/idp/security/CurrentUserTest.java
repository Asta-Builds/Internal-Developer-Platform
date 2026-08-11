package com.idp.security;

import com.idp.domain.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reads the reconciled internal principal out of the security context.
 */
class CurrentUserTest {

    private final AuthenticatedUser actor = AuthenticatedUser.builder()
            .id("usr-1").username("alice").role(Role.ADMIN).team("Platform").build();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("resolves the internal principal when it is the authentication principal")
    void resolvesInternalPrincipal() {
        var authentication = new UsernamePasswordAuthenticationToken(actor, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(CurrentUser.get()).contains(actor);
        assertThat(CurrentUser.require()).isEqualTo(actor);
    }

    @Test
    @DisplayName("is empty when no authentication is bound")
    void emptyWithoutAuthentication() {
        assertThat(CurrentUser.get()).isEmpty();
        assertThat(CurrentUser.from(null)).isEmpty();
    }

    @Test
    @DisplayName("is empty when the principal is not the internal user")
    void emptyForForeignPrincipal() {
        var authentication = new UsernamePasswordAuthenticationToken("just-a-string", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(CurrentUser.get()).isEmpty();
    }

    @Test
    @DisplayName("require throws when a protected path runs without a principal")
    void requireThrowsWithoutPrincipal() {
        assertThatThrownBy(CurrentUser::require)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No authenticated internal user");
    }
}
