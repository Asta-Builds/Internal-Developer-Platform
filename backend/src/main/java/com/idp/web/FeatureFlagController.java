package com.idp.web;

import com.idp.domain.FeatureFlagEntity;
import com.idp.service.FeatureFlagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/feature-flags")
@RequiredArgsConstructor
public class FeatureFlagController {

    private final FeatureFlagService flagService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'FEATURE_FLAG', 'READ')")
    public ResponseEntity<List<FeatureFlagEntity>> getAllFlags() {
        return ResponseEntity.ok(flagService.getAllFlags());
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'FEATURE_FLAG', 'CREATE')")
    public ResponseEntity<FeatureFlagEntity> createFlag(@RequestBody FeatureFlagEntity flag) {
        return ResponseEntity.ok(flagService.createFlag(flag));
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasPermission(#id, 'FEATURE_FLAG', 'UPDATE')")
    public ResponseEntity<FeatureFlagEntity> toggleFlag(@PathVariable String id) {
        return ResponseEntity.ok(flagService.toggleFlag(id));
    }

    /**
     * The rollout ceiling depends on the percentage being requested, which
     * {@code hasPermission} cannot carry — hence the named guard.
     */
    @PatchMapping("/{id}/rollout")
    @PreAuthorize("@authz.canRollout(#id, #payload['rolloutPercent'])")
    public ResponseEntity<FeatureFlagEntity> updateRollout(
            @PathVariable String id,
            @RequestBody Map<String, Integer> payload) {
        int percentage = payload.getOrDefault("rolloutPercent", 0);
        return ResponseEntity.ok(flagService.updateRollout(id, percentage));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(#id, 'FEATURE_FLAG', 'DELETE')")
    public ResponseEntity<Void> deleteFlag(@PathVariable String id) {
        flagService.deleteFlag(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/eval/{key}")
    @PreAuthorize("hasPermission(#key, 'FEATURE_FLAG', 'READ')")
    public ResponseEntity<Map<String, Object>> evaluateFlag(
            @PathVariable String key,
            @RequestParam(defaultValue = "anonymous") String userId) {
        return ResponseEntity.ok(flagService.evaluateFlag(key, userId));
    }
}
