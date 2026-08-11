package com.idp.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The CORS surface is explicit: only the portal origins may call the API, and the
 * Authorization header must survive preflight.
 */
class SecurityConfigTest {

    private SecurityConfig config;

    @BeforeEach
    void setUp() {
        config = new SecurityConfig(
                mock(JwtAuthenticationConverter.class),
                mock(ProblemDetailAuthErrorHandler.class));
        org.springframework.test.util.ReflectionTestUtils.setField(config, "allowedOrigins",
                List.of("http://localhost:4200", "https://portal.example.com"));
        org.springframework.test.util.ReflectionTestUtils.setField(config, "h2ConsoleEnabled", false);
    }

    @Test
    @DisplayName("exposes exactly the configured origins and headers")
    void corsConfiguration() {
        CorsConfigurationSource source = config.corsConfigurationSource();
        CorsConfiguration cors = source.getCorsConfiguration(new MockHttpServletRequest());

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins()).containsExactly("http://localhost:4200", "https://portal.example.com");
        assertThat(cors.getAllowedHeaders()).contains("Authorization", "Content-Type", "Idempotency-Key");
        assertThat(cors.getAllowedMethods()).contains("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(cors.getExposedHeaders()).contains("X-Correlation-Id");
        assertThat(cors.getAllowCredentials()).isTrue();
        assertThat(cors.getMaxAge()).isEqualTo(3600L);
    }

    @Test
    @DisplayName("a blank jwk-set-uri still produces a working decoder")
    void jwtDecoderWithFallbackUri() {
        assertThat(config.jwtDecoder("http://localhost:8180/realms/idp-realm", ""))
                .isNotNull();
    }

    @Test
    @DisplayName("an explicit jwk-set-uri is honoured")
    void jwtDecoderWithExplicitUri() {
        assertThat(config.jwtDecoder("http://localhost:8180/realms/idp-realm",
                "http://keycloak:8180/realms/idp-realm/protocol/openid-connect/certs"))
                .isNotNull();
    }
}
