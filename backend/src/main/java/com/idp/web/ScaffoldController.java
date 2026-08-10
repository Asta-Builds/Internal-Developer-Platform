package com.idp.web;

import com.idp.domain.ScaffoldJobEntity;
import com.idp.dto.ScaffoldRequestDto;
import com.idp.service.ScaffoldingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/scaffold")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ScaffoldController {

    private final ScaffoldingService scaffoldingService;

    @PostMapping
    public ResponseEntity<ScaffoldJobEntity> initiateScaffold(
            @RequestBody ScaffoldRequestDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        
        ScaffoldJobEntity job = scaffoldingService.initiateScaffold(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<ScaffoldJobEntity> getJobStatus(@PathVariable String jobId) {
        return ResponseEntity.ok(scaffoldingService.getJobStatus(jobId));
    }

    @GetMapping(value = "/jobs/{jobId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamJobProgress(@PathVariable String jobId) {
        return scaffoldingService.subscribeJobStream(jobId);
    }

    @GetMapping("/templates")
    public ResponseEntity<List<Map<String, String>>> getAvailableTemplates() {
        List<Map<String, String>> templates = List.of(
            Map.of("id", "SPRING_BOOT", "name", "Spring Boot + Angular + PostgreSQL", "description", "Standard enterprise REST API stack with Angular UI and GitHub Actions"),
            Map.of("id", "ANGULAR", "name", "Angular SPA Frontend", "description", "Standalone Angular 17/18 web app with Signals and HeroUI Dark aesthetics"),
            Map.of("id", "GO", "name", "Go Microservice (Gin / GORM)", "description", "High-performance lightweight microservice with Docker containerization"),
            Map.of("id", "PYTHON", "name", "Python FastAPI + Asyncpg", "description", "Async Python REST API for machine learning or event consumers")
        );
        return ResponseEntity.ok(templates);
    }
}
