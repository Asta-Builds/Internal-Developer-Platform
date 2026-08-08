package com.idp.service;

import com.idp.domain.FeatureFlagEntity;
import com.idp.repository.FeatureFlagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    public List<FeatureFlagEntity> getAllFlags() {
        return flagRepository.findAll();
    }

    @Transactional
    public FeatureFlagEntity createFlag(FeatureFlagEntity flag) {
        if (flag.getId() == null) {
            flag.setId("ff-" + UUID.randomUUID().toString().substring(0, 6));
        }
        flag.setUpdatedAt(LocalDateTime.now());
        FeatureFlagEntity saved = flagRepository.save(flag);
        auditService.logAction("admin", "FEATURE_FLAG_CREATED", saved.getKey(), "Created feature flag " + saved.getKey());
        return saved;
    }

    @Transactional
    public FeatureFlagEntity toggleFlag(String id) {
        FeatureFlagEntity flag = flagRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Feature flag not found: " + id));
        flag.setEnabled(!flag.isEnabled());
        flag.setUpdatedAt(LocalDateTime.now());
        FeatureFlagEntity saved = flagRepository.save(flag);
        auditService.logAction("admin", "FEATURE_FLAG_TOGGLED", saved.getKey(), "Flag status changed to " + saved.isEnabled());
        return saved;
    }

    @Transactional
    public FeatureFlagEntity updateRolloutPercent(String id, int rolloutPercent) {
        FeatureFlagEntity flag = flagRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Feature flag not found: " + id));
        flag.setRolloutPercent(Math.min(100, Math.max(0, rolloutPercent)));
        flag.setUpdatedAt(LocalDateTime.now());
        FeatureFlagEntity saved = flagRepository.save(flag);
        auditService.logAction("admin", "FEATURE_FLAG_ROLLOUT_UPDATED", saved.getKey(), "Canary rollout percentage set to " + saved.getRolloutPercent() + "%");
        return saved;
    }

    @Transactional
    public FeatureFlagEntity updateRollout(String id, int rolloutPercent) {
        return updateRolloutPercent(id, rolloutPercent);
    }

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
