package com.idp.web;

import com.idp.domain.FeatureFlagEntity;
import com.idp.service.FeatureFlagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Feature flag endpoints delegate to the service and map HTTP outcomes.
 */
@ExtendWith(MockitoExtension.class)
class FeatureFlagControllerTest {

    @Mock private FeatureFlagService flagService;

    private FeatureFlagController controller;

    @BeforeEach
    void setUp() {
        controller = new FeatureFlagController(flagService);
    }

    private final FeatureFlagEntity flag = FeatureFlagEntity.builder()
            .id("ff-1").key("PAY_V2").enabled(true).rolloutPercent(50).build();

    @Test
    @DisplayName("lists all flags")
    void listsFlags() {
        when(flagService.getAllFlags()).thenReturn(List.of(flag));

        assertThat(controller.getAllFlags().getBody()).containsExactly(flag);
    }

    @Test
    @DisplayName("creates a flag")
    void createsFlag() {
        when(flagService.createFlag(flag)).thenReturn(flag);

        assertThat(controller.createFlag(flag).getBody()).isEqualTo(flag);
    }

    @Test
    @DisplayName("toggles a flag by id")
    void togglesFlag() {
        when(flagService.toggleFlag("ff-1")).thenReturn(flag);

        assertThat(controller.toggleFlag("ff-1").getBody()).isEqualTo(flag);
    }

    @Test
    @DisplayName("rollout passes the requested percentage through")
    void updatesRollout() {
        when(flagService.updateRollout("ff-1", 75)).thenReturn(flag);

        assertThat(controller.updateRollout("ff-1", Map.of("rolloutPercent", 75)).getBody()).isEqualTo(flag);
    }

    @Test
    @DisplayName("rollout defaults to zero when the payload is absent")
    void rolloutDefaults() {
        when(flagService.updateRollout("ff-1", 0)).thenReturn(flag);

        assertThat(controller.updateRollout("ff-1", Map.of()).getBody()).isEqualTo(flag);
    }

    @Test
    @DisplayName("deleting a flag returns 204")
    void deletesFlag() {
        assertThat(controller.deleteFlag("ff-1").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(flagService).deleteFlag("ff-1");
    }

    @Test
    @DisplayName("evaluates a flag for a user")
    void evaluatesFlag() {
        when(flagService.evaluateFlag("PAY_V2", "user-1"))
                .thenReturn(Map.of("enabled", true, "key", "PAY_V2"));

        var result = controller.evaluateFlag("PAY_V2", "user-1").getBody();

        assertThat(result.get("enabled")).isEqualTo(true);
    }
}
