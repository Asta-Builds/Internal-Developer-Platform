package com.idp.service;

import com.idp.domain.FeatureFlagEntity;
import com.idp.repository.FeatureFlagRepository;
import com.idp.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeatureFlagService {

    private final FeatureFlagRepository flagRepository;
    private final AuditService auditService;
    private final RabbitTemplate rabbitTemplate;

    @Cacheable(value = "feature_flags", key = "'all_flags'")
    public List<FeatureFlagEntity> getAllFlags() {
        log.debug("[Cache Miss] Fetching feature flags from database");
        return flagRepository.findAll();
    }

    @Transactional
    @CacheEvict(value = "feature_flags", allEntries = true)
    public FeatureFlagEntity createFlag(FeatureFlagEntity flag) {
        if (flag.getId() == null) {
            flag.setId("ff-" + UUID.randomUUID().toString().substring(0, 6));
        }
        flag.setUpdatedAt(LocalDateTime.now());
        FeatureFlagEntity saved = flagRepository.save(flag);
        auditService.logAction(currentActor(), "FEATURE_FLAG_CREATED", saved.getKey(), "Created feature flag " + saved.getKey());
        publishFlagUpdateEvent("CREATED", saved);
        return saved;
    }

    @Transactional
    @CacheEvict(value = "feature_flags", allEntries = true)
    public FeatureFlagEntity toggleFlag(String id) {
        FeatureFlagEntity flag = flagRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Feature flag not found: " + id));
        flag.setEnabled(!flag.isEnabled());
        flag.setUpdatedAt(LocalDateTime.now());
        FeatureFlagEntity saved = flagRepository.save(flag);
        auditService.logAction(currentActor(), "FEATURE_FLAG_TOGGLED", saved.getKey(), "Flag status changed to " + saved.isEnabled());
        publishFlagUpdateEvent("TOGGLED", saved);
        return saved;
    }

    @Transactional
    @CacheEvict(value = "feature_flags", allEntries = true)
    public FeatureFlagEntity updateRolloutPercent(String id, int rolloutPercent) {
        FeatureFlagEntity flag = flagRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Feature flag not found: " + id));
        flag.setRolloutPercent(Math.min(100, Math.max(0, rolloutPercent)));
        flag.setUpdatedAt(LocalDateTime.now());
        FeatureFlagEntity saved = flagRepository.save(flag);
        auditService.logAction(currentActor(), "FEATURE_FLAG_ROLLOUT_UPDATED", saved.getKey(), "Canary rollout percentage set to " + saved.getRolloutPercent() + "%");
        publishFlagUpdateEvent("ROLLOUT_UPDATED", saved);
        return saved;
    }

    @Transactional
    @CacheEvict(value = "feature_flags", allEntries = true)
    public FeatureFlagEntity updateRollout(String id, int rolloutPercent) {
        return updateRolloutPercent(id, rolloutPercent);
    }

    @Transactional
    @CacheEvict(value = "feature_flags", allEntries = true)
    public void deleteFlag(String id) {
        FeatureFlagEntity flag = flagRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Feature flag not found: " + id));
        flagRepository.delete(flag);
        auditService.logAction(currentActor(), "FEATURE_FLAG_DELETED", flag.getKey(),
                "Removed feature flag " + flag.getKey());
        publishFlagUpdateEvent("DELETED", flag);
    }

    /** Resolves the acting user so the audit trail names a real principal. */
    private String currentActor() {
        return CurrentUser.get()
                .map(user -> user.toActorId())
                .orElse("system");
    }

    private void publishFlagUpdateEvent(String action, FeatureFlagEntity flag) {
        try {
            rabbitTemplate.convertAndSend("idp.direct.exchange", "flag.updated.routing.key", Map.of(
                    "action", action,
                    "flagKey", flag.getKey(),
                    "enabled", flag.isEnabled(),
                    "rolloutPercent", flag.getRolloutPercent(),
                    "updatedAt", LocalDateTime.now().toString()
            ));
            log.info("Broadcasted AMQP flag.updated event for flag key {}", flag.getKey());
        } catch (Exception e) {
            log.warn("Failed to publish AMQP flag.updated event: {}", e.getMessage());
        }
    }

    @Cacheable(value = "feature_flags", key = "#key + '_' + #userId")
    public Map<String, Object> evaluateFlag(String key, String userId) {
        Optional<FeatureFlagEntity> flagOpt = flagRepository.findByKey(key);
        if (flagOpt.isEmpty()) {
            return Map.of("enabled", false, "reason", "FLAG_NOT_FOUND", "key", key);
        }

        FeatureFlagEntity flag = flagOpt.get();
        if (!flag.isEnabled()) {
            return Map.of("enabled", false, "reason", "FLAG_DISABLED", "key", key);
        }

        if (userId == null || userId.isBlank()) {
            return Map.of("enabled", flag.getRolloutPercent() == 100, "reason", "NO_USER_ID", "key", key);
        }

        int userBucket = Math.abs((userId + "_" + key).hashCode() % 100);
        boolean activeForUser = userBucket < flag.getRolloutPercent();

        return Map.of(
                "enabled", activeForUser,
                "key", key,
                "userBucket", userBucket,
                "rolloutPercent", flag.getRolloutPercent(),
                "reason", activeForUser ? "CANARY_MATCH" : "OUTSIDE_ROLLOUT_BUCKET"
        );
    }
}
