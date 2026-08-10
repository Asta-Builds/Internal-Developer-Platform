package com.idp.web;

import com.idp.dto.ServiceHealthDto;
import com.idp.service.ObservabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ObservabilityController {

    private final ObservabilityService observabilityService;

    @GetMapping("/services/{id}/health")
    @PreAuthorize("hasPermission(#id, 'OBSERVABILITY', 'READ')")
    public ResponseEntity<ServiceHealthDto> getServiceHealth(@PathVariable String id) {
        return ResponseEntity.ok(observabilityService.getServiceHealth(id));
    }

    @GetMapping("/telemetry/metrics")
    @PreAuthorize("hasPermission(null, 'OBSERVABILITY', 'READ')")
    public ResponseEntity<Map<String, Object>> getTelemetryMetrics() {
        return ResponseEntity.ok(observabilityService.getTelemetryMetrics());
    }

    /**
     * Live log streaming is team-scoped below TECH_LEAD, so the target service id
     * drives the ABAC ownership check.
     */
    @GetMapping(value = "/telemetry/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasPermission(#serviceId, 'OBSERVABILITY', 'STREAM')")
    public SseEmitter streamLiveLogs(@RequestParam(required = false) String serviceId) {
        return observabilityService.streamLiveLogs(serviceId);
    }
}
