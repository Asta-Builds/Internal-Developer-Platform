package com.idp.web;

import com.idp.domain.FeatureFlagEntity;
import com.idp.service.FeatureFlagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/feature-flags")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FeatureFlagController {

    private final FeatureFlagService flagService;

    @GetMapping
    public ResponseEntity<List<FeatureFlagEntity>> getAllFlags() {
        return ResponseEntity.ok(flagService.getAllFlags());
    }

    @PostMapping
    public ResponseEntity<FeatureFlagEntity> createFlag(@RequestBody FeatureFlagEntity flag) {
        return ResponseEntity.ok(flagService.createFlag(flag));
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<FeatureFlagEntity> toggleFlag(@PathVariable String id) {
        return ResponseEntity.ok(flagService.toggleFlag(id));
    }

    @PatchMapping("/{id}/rollout")
    public ResponseEntity<FeatureFlagEntity> updateRollout(
            @PathVariable String id,
            @RequestBody Map<String, Integer> payload) {
        int percentage = payload.getOrDefault("rolloutPercent", 0);
        return ResponseEntity.ok(flagService.updateRollout(id, percentage));
    }

    @GetMapping("/eval/{key}")
    public ResponseEntity<Map<String, Object>> evaluateFlag(
            @PathVariable String key,
            @RequestParam(defaultValue = "anonymous") String userId) {
        return ResponseEntity.ok(flagService.evaluateFlag(key, userId));
    }
}
