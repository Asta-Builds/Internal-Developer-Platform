package com.idp.web;

import com.idp.service.EventReplayService;
import com.idp.service.ResiliencePolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Enterprise Resilience, Status, and Event Replay / Simulation surface.
 */
@RestController
@RequestMapping("/api/v1/enterprise")
@RequiredArgsConstructor
public class EnterpriseController {

    private final ResiliencePolicyService resiliencePolicyService;
    private final EventReplayService eventReplayService;

    @GetMapping("/resilience")
    @PreAuthorize("hasPermission(null, 'DEVOPS', 'READ')")
    public ResponseEntity<Map<String, Object>> getResilience() {
        return ResponseEntity.ok(resiliencePolicyService.getResilienceStatus());
    }

    @GetMapping("/status-page")
    @PreAuthorize("hasPermission(null, 'DEVOPS', 'READ')")
    public ResponseEntity<Map<String, Object>> getStatusPage() {
        return ResponseEntity.ok(resiliencePolicyService.getInternalStatusPage());
    }

    @GetMapping("/dlq")
    @PreAuthorize("hasPermission(null, 'DEVOPS', 'READ')")
    public ResponseEntity<Map<String, Object>> getDlqStatus() {
        return ResponseEntity.ok(eventReplayService.getDlqStatus());
    }

    @PostMapping("/events/replay")
    @PreAuthorize("hasPermission(null, 'DEVOPS', 'EXECUTE')")
    public ResponseEntity<Map<String, Object>> replayEvent(@RequestBody Map<String, Object> payload) {
        String eventId = payload.get("eventId") != null ? String.valueOf(payload.get("eventId")) : null;
        String idempotencyKey = payload.get("idempotencyKey") != null ? String.valueOf(payload.get("idempotencyKey")) : null;
        boolean dryRun = Boolean.TRUE.equals(payload.get("dryRun"));
        @SuppressWarnings("unchecked")
        Map<String, Object> eventData = (Map<String, Object>) payload.get("payload");

        Map<String, Object> result = eventReplayService.replayEvent(eventId, idempotencyKey, eventData, dryRun);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/dry-run/simulate")
    @PreAuthorize("hasPermission(null, 'DEVOPS', 'EXECUTE')")
    public ResponseEntity<Map<String, Object>> dryRunSimulation(@RequestBody Map<String, Object> payload) {
        String eventId = payload.get("eventId") != null ? String.valueOf(payload.get("eventId")) : null;
        String idempotencyKey = payload.get("idempotencyKey") != null ? String.valueOf(payload.get("idempotencyKey")) : null;
        @SuppressWarnings("unchecked")
        Map<String, Object> eventData = (Map<String, Object>) payload.get("payload");

        Map<String, Object> result = eventReplayService.replayEvent(eventId, idempotencyKey, eventData, true);
        return ResponseEntity.ok(result);
    }
}
