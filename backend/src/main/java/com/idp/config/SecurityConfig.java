package com.idp.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Resource-server configuration.
 *
 * <p>Keycloak's role here ends at signature and issuer verification. Once the token
 * is proven authentic, {@link JwtAuthenticationConverter} swaps it for the internal
 * user record and all authorization is decided by the platform's own RBAC/ABAC engine.
 *
 * <p>The chain is deny-by-default: anything not explicitly listed below requires an
 * authenticated principal, and the endpoints themselves carry {@code @PreAuthorize}.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationConverter jwtAuthenticationConverter;
    private final ProblemDetailAuthErrorHandler authErrorHandler;

    @Value("${idp.security.cors.allowed-origins:http://localhost:4200,http://localhost:80}")
    private List<String> allowedOrigins;

    @Value("${idp.security.h2-console.enabled:false}")
    private boolean h2ConsoleEnabled;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Stateless bearer-token API: no session to fixate, no CSRF token to steal.
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                // Liveness probe and CORS preflight must stay reachable.
                auth.requestMatchers("/api/health", "/actuator/health/**").permitAll();
                // OpenAPI 3.0 contract + Swagger UI (read-only documentation).
                auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                auth.requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll();

                if (h2ConsoleEnabled) {
                    // Local development only; disabled unless explicitly switched on.
                    auth.requestMatchers("/h2-console/**").permitAll();
                }

                auth.anyRequest().authenticated();
            })
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                .authenticationEntryPoint(authErrorHandler))
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(authErrorHandler)
                .accessDeniedHandler(authErrorHandler));

        if (h2ConsoleEnabled) {
            http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        }

        return http.build();
    }

    /**
     * Built from the JWK set rather than {@code issuer-uri} so that startup does not
     * block on OIDC discovery when Keycloak is not yet reachable; keys are fetched on
     * first use. The issuer is still validated explicitly, alongside expiry.
     */
    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${idp.security.issuer-uri:http://localhost:8180/realms/idp-realm}") String issuerUri,
            @Value("${idp.security.jwk-set-uri:}") String configuredJwkSetUri) {

        String jwkSetUri = configuredJwkSetUri.isBlank()
                ? issuerUri + "/protocol/openid-connect/certs"
                : configuredJwkSetUri;

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuerUri));
        return decoder;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Explicit origins: a wildcard cannot be combined with credentials, and the
        // portal sends an Authorization header on every call.
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key",
                "X-Correlation-Id", "Accept", "Origin"));
        config.setExposedHeaders(List.of("X-Correlation-Id"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
