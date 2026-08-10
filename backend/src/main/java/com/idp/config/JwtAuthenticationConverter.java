package com.idp.config;

import com.idp.security.AuthenticatedUser;
import com.idp.security.InternalUserReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Converts a verified Keycloak token into an authentication whose principal is the
 * <em>internal</em> user record.
 *
 * <p>This is where the AuthN/AuthZ boundary is enforced. Keycloak's
 * {@code realm_access.roles} claim is never promoted to a granted authority; the
 * token is reduced to an identity, that identity is reconciled against the
 * {@code users} table, and the role stored there becomes the single authority.
 * Fine-grained decisions are then made by the RBAC/ABAC engine, not by this claim.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final InternalUserReconciliationService reconciliationService;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        AuthenticatedUser user = reconciliationService.reconcile(jwt)
                .orElseThrow(() -> new InvalidBearerTokenException(
                        "Token is valid but maps to no active internal user account"));

        // A single coarse authority derived from the internal record. Every real
        // decision goes through PolicyDecisionService.
        List<GrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));

        return new InternalUserAuthenticationToken(jwt, authorities, user);
    }

    /**
     * A {@link JwtAuthenticationToken} whose principal is the reconciled internal user
     * rather than the raw JWT, so {@code CurrentUser} can read role and team directly.
     */
    public static class InternalUserAuthenticationToken extends JwtAuthenticationToken {

        private final transient AuthenticatedUser internalUser;

        public InternalUserAuthenticationToken(Jwt jwt,
                                               List<GrantedAuthority> authorities,
                                               AuthenticatedUser internalUser) {
            super(jwt, authorities, internalUser.getUsername());
            this.internalUser = internalUser;
        }

        @Override
        public Object getPrincipal() {
            return internalUser;
        }
    }
}
