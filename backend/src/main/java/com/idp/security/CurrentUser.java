package com.idp.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Reads the reconciled internal principal out of the security context.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AuthenticatedUser> get() {
        return from(SecurityContextHolder.getContext().getAuthentication());
    }

    public static Optional<AuthenticatedUser> from(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    /**
     * @throws IllegalStateException when no internal principal is bound — which means a
     *         protected code path was reached without passing the security filter chain.
     */
    public static AuthenticatedUser require() {
        return get().orElseThrow(() ->
                new IllegalStateException("No authenticated internal user bound to the current request"));
    }
}
