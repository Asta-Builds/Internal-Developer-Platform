package com.idp.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Liveness probe — deliberately unauthenticated and side-effect free.
 */
class HealthControllerTest {

    private HealthController controller;

    @BeforeEach
    void setUp() {
        controller = new HealthController();
    }

    @Test
    @DisplayName("reports UP with the backend service name")
    void health() {
        Map<String, Object> body = controller.getHealth().getBody();

        assertThat(body.get("status")).isEqualTo("UP");
        assertThat(body.get("service")).isEqualTo("idp-backend");
        assertThat(body.get("timestamp")).isInstanceOf(Long.class);
    }
}
