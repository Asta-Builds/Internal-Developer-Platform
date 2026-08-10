package com.idp.config;

import com.idp.security.IdpPermissionEvaluator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Wires the platform's {@link IdpPermissionEvaluator} into method security so that
 * {@code @PreAuthorize("hasPermission(...)")} resolves against the internal RBAC/ABAC
 * engine.
 *
 * <p>Kept apart from {@link SecurityConfig} so that building the expression handler
 * does not pull the filter chain into premature initialisation.
 */
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {

    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(IdpPermissionEvaluator permissionEvaluator) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(permissionEvaluator);
        return handler;
    }
}
