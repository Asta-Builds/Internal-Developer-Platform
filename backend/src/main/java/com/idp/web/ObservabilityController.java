package com.idp.web;

import com.idp.dto.ServiceHealthDto;
import com.idp.service.ObservabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ObservabilityController {

    private final ObservabilityService observabilityService;

    @GetMapping("/services/{id}/health")
    public ResponseEntity<ServiceHealthDto> getServiceHealth(@PathVariable String id) {
        return ResponseEntity.ok(observabilityService.getServiceHealth(id));
    }

    @GetMapping("/telemetry/metrics")
    public ResponseEntity<Map<String, Object>> getTelemetryMetrics() {
        return ResponseEntity.ok(observabilityService.getTelemetryMetrics());
    }

    @GetMapping(value = "/telemetry/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLiveLogs(@RequestParam(required = false) String serviceId) {
        return observabilityService.streamLiveLogs(serviceId);
    }
}
