package com.idp.service;

import com.idp.domain.FeatureFlagEntity;
import com.idp.repository.FeatureFlagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Feature flag lifecycle plus the hash-based canary evaluation.
 */
@ExtendWith(MockitoExtension.class)
class FeatureFlagServiceTest {

    @Mock private FeatureFlagRepository flagRepository;
    @Mock private AuditService auditService;
    @Mock private RabbitTemplate rabbitTemplate;

    private FeatureFlagService service;

    @BeforeEach
    void setUp() {
        service = new FeatureFlagService(flagRepository, auditService, rabbitTemplate);
        SecurityContextHolder.clearContext();
    }

    private FeatureFlagEntity flag(String id, String key, boolean enabled, int rollout) {
        return FeatureFlagEntity.builder()
                .id(id).key(key).enabled(enabled).rolloutPercent(rollout).build();
    }

    @Test
    @DisplayName("createFlag assigns an id and stamps the update time")
    void createsFlag() {
        FeatureFlagEntity incoming = flag(null, "PAY_V2", true, 10);
        FeatureFlagEntity saved = flag("ff-abc123", "PAY_V2", true, 10);
        when(flagRepository.save(any(FeatureFlagEntity.class))).thenReturn(saved);

        FeatureFlagEntity result = service.createFlag(incoming);

        assertThat(result.getId()).isEqualTo("ff-abc123");
        assertThat(incoming.getUpdatedAt()).isNotNull();
        verify(auditService).logAction(anyString(), org.mockito.ArgumentMatchers.eq("FEATURE_FLAG_CREATED"),
                anyString(), anyString());
        verify(rabbitTemplate).convertAndSend(org.mockito.ArgumentMatchers.eq("idp.direct.exchange"),
                anyString(), any(Map.class));
    }

    @Test
    @DisplayName("toggleFlag flips the enabled bit")
    void togglesFlag() {
        FeatureFlagEntity existing = flag("ff-1", "PAY_V2", false, 0);
        FeatureFlagEntity toggled = flag("ff-1", "PAY_V2", true, 0);
        when(flagRepository.findById("ff-1")).thenReturn(Optional.of(existing));
        when(flagRepository.save(existing)).thenReturn(toggled);

        FeatureFlagEntity result = service.toggleFlag("ff-1");

        assertThat(result.isEnabled()).isTrue();
        verify(auditService).logAction(anyString(), org.mockito.ArgumentMatchers.eq("FEATURE_FLAG_TOGGLED"),
                anyString(), anyString());
    }

    @Test
    @DisplayName("updateRolloutPercent clamps into 0..100")
    void clampsRollout() {
        FeatureFlagEntity existing = flag("ff-1", "PAY_V2", true, 30);
        when(flagRepository.findById("ff-1")).thenReturn(Optional.of(existing));
        when(flagRepository.save(existing)).thenReturn(existing);

        service.updateRolloutPercent("ff-1", 150);
        assertThat(existing.getRolloutPercent()).isEqualTo(100);

        service.updateRolloutPercent("ff-1", -5);
        assertThat(existing.getRolloutPercent()).isEqualTo(0);
    }

    @Test
    @DisplayName("mutations on a missing flag throw NoSuchElementException")
    void missingFlagThrows() {
        when(flagRepository.findById("ff-missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.toggleFlag("ff-missing"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Feature flag not found");
    }

    @Test
    @DisplayName("evaluateFlag reports unknown keys as disabled")
    void unknownKeyDisabled() {
        when(flagRepository.findByKey("NOPE")).thenReturn(Optional.empty());

        assertThat(service.evaluateFlag("NOPE", "u-1"))
                .containsEntry("enabled", false)
                .containsEntry("reason", "FLAG_NOT_FOUND");
    }

    @Test
    @DisplayName("evaluateFlag reports disabled flags")
    void disabledFlag() {
        when(flagRepository.findByKey("PAY_V2")).thenReturn(Optional.of(flag("ff-1", "PAY_V2", false, 80)));

        assertThat(service.evaluateFlag("PAY_V2", "u-1"))
                .containsEntry("enabled", false)
                .containsEntry("reason", "FLAG_DISABLED");
    }

    @Test
    @DisplayName("evaluateFlag buckets the user deterministically against the rollout")
    void deterministicBucketing() {
        when(flagRepository.findByKey("PAY_V2")).thenReturn(Optional.of(flag("ff-1", "PAY_V2", true, 100)));

        Map<String, Object> first = service.evaluateFlag("PAY_V2", "user-42");
        Map<String, Object> second = service.evaluateFlag("PAY_V2", "user-42");

        assertThat(first.get("enabled")).isEqualTo(true);
        assertThat(first.get("userBucket")).isEqualTo(second.get("userBucket"));
        assertThat(first).containsEntry("reason", "CANARY_MATCH");
    }

    @Test
    @DisplayName("a failed AMQP publish never fails the operation")
    void amqpFailureTolerated() {
        FeatureFlagEntity existing = flag("ff-1", "PAY_V2", false, 10);
        when(flagRepository.findById("ff-1")).thenReturn(Optional.of(existing));
        when(flagRepository.save(existing)).thenReturn(existing);
        doThrow(new RuntimeException("broker down")).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(Object.class));

        assertThat(service.toggleFlag("ff-1").isEnabled()).isTrue();
    }
}
