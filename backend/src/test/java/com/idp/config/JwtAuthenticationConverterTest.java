package com.idp.config;

import com.idp.config.JwtAuthenticationConverter.InternalUserAuthenticationToken;
import com.idp.domain.Role;
import com.idp.security.AuthenticatedUser;
import com.idp.security.InternalUserReconciliationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Keycloak tokens are reduced to an internal identity here; the IdP role claim is
 * never promoted to a granted authority.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationConverterTest {

    @Mock private InternalUserReconciliationService reconciliationService;

    private JwtAuthenticationConverter converter;

    private Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("sub-1")
                .claim("preferred_username", "alice")
                .issuer("http://localhost:8180/realms/idp-realm")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    @Test
    @DisplayName("converts a reconcilable token into an internal principal")
    void convertsToken() {
        converter = new JwtAuthenticationConverter(reconciliationService);
        Jwt token = jwt();
        AuthenticatedUser actor = AuthenticatedUser.builder()
                .id("usr-1").username("alice").role(Role.TECH_LEAD).team("Equipe Paiement").build();
        when(reconciliationService.reconcile(token)).thenReturn(Optional.of(actor));

        InternalUserAuthenticationToken converted = (InternalUserAuthenticationToken) converter.convert(token);

        assertThat(converted.getPrincipal()).isEqualTo(actor);
        assertThat(converted.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_TECH_LEAD");
    }

    @Test
    @DisplayName("rejects a token that maps to no internal user")
    void rejectsUnreconcilableToken() {
        converter = new JwtAuthenticationConverter(reconciliationService);
        Jwt token = jwt();
        when(reconciliationService.reconcile(token)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> converter.convert(token))
                .isInstanceOf(InvalidBearerTokenException.class)
                .hasMessageContaining("no active internal user");
    }
}
