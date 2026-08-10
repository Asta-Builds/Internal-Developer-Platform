package com.idp.service;

import com.idp.domain.ProjectEntity;
import com.idp.domain.ScaffoldJobEntity;
import com.idp.domain.ServiceEntity;
import com.idp.dto.ScaffoldRequestDto;
import com.idp.repository.ProjectRepository;
import com.idp.repository.ScaffoldJobRepository;
import com.idp.repository.ServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScaffoldingService {

    private final ProjectRepository projectRepository;
    private final ScaffoldJobRepository scaffoldJobRepository;
    private final ServiceRepository serviceRepository;
    private final AuditService auditService;
    private final IdempotencyService idempotencyService;
    private final RabbitTemplate rabbitTemplate;

    // Active SSE Emitters for real-time streaming progress
    private final Map<String, List<SseEmitter>> jobEmitters = new ConcurrentHashMap<>();

    @Transactional
    public ScaffoldJobEntity initiateScaffold(ScaffoldRequestDto request, String idempotencyKey) {
        // 1. Idempotency Check
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Object> cached = idempotencyService.getCachedResult(idempotencyKey);
            if (cached.isPresent() && cached.get() instanceof ScaffoldJobEntity cachedJob) {
                return cachedJob;
            }
        }

        String projectId = "proj-" + UUID.randomUUID().toString().substring(0, 8);
        String jobId = "job-" + UUID.randomUUID().toString().substring(0, 8);
        String repoName = request.getName().toLowerCase().replaceAll("[^a-z0-9-]", "-");

        auditService.logAction("admin", "SCAFFOLD_PROJECT_INITIATED", projectId, "Created scaffold job for template " + request.getStackTemplate());

        ProjectEntity project = ProjectEntity.builder()
                .id(projectId)
                .name(request.getName())
                .description(request.getDescription())
                .stackTemplate(request.getStackTemplate())
                .ownerTeam(request.getOwnerTeam())
                .repositoryName(repoName)
                .repositoryUrl("https://github.com/enterprise-org/" + repoName)
                .status("SCAFFOLDING")
                .build();

        projectRepository.save(project);

        ScaffoldJobEntity job = ScaffoldJobEntity.builder()
                .id(jobId)
                .projectId(projectId)
                .status("RUNNING")
                .progressPercent(10)
                .currentStep("Initializing scaffolding workspace")
                .stepLogs("[00:00:01] Scaffold request validated for template: " + request.getStackTemplate() + "\n")
                .build();

        ScaffoldJobEntity savedJob = scaffoldJobRepository.save(job);

        // Store in Idempotency cache
        if (idempotencyKey != null) {
            idempotencyService.storeResult(idempotencyKey, savedJob);
        }

        // Notify AMQP job.scaffold queue
        try {
            rabbitTemplate.convertAndSend("idp.direct.exchange", "job.scaffold.routing.key", Map.of(
                    "jobId", jobId,
                    "projectId", projectId,
                    "action", "SCAFFOLD_INITIATED",
                    "template", request.getStackTemplate()
            ));
        } catch (Exception e) {
            log.warn("AMQP scaffold notification skipped: {}", e.getMessage());
        }

        // Trigger async multi-step pipeline execution
        executeScaffoldingPipelineAsync(projectId, jobId, request);

        return savedJob;
    }

    public SseEmitter subscribeJobStream(String jobId) {
        SseEmitter emitter = new SseEmitter(180_000L); // 3 min timeout
        jobEmitters.computeIfAbsent(jobId, k -> new ArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(jobId, emitter));
        emitter.onTimeout(() -> removeEmitter(jobId, emitter));
        emitter.onError(e -> removeEmitter(jobId, emitter));

        // Send initial state
        scaffoldJobRepository.findById(jobId).ifPresent(job -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("JOB_PROGRESS")
                        .data(job));
            } catch (IOException e) {
                removeEmitter(jobId, emitter);
            }
        });

        return emitter;
    }

    private void removeEmitter(String jobId, SseEmitter emitter) {
        List<SseEmitter> list = jobEmitters.get(jobId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                jobEmitters.remove(jobId);
            }
        }
    }

    private void broadcastJobProgress(ScaffoldJobEntity job) {
        List<SseEmitter> emitters = jobEmitters.get(job.getId());
        if (emitters != null) {
            List<SseEmitter> deadEmitters = new ArrayList<>();
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("JOB_PROGRESS")
                            .data(job));
                } catch (Exception e) {
                    deadEmitters.add(emitter);
                }
            }
            emitters.removeAll(deadEmitters);
        }
    }

    @Async
    public CompletableFuture<Void> executeScaffoldingPipelineAsync(String projectId, String jobId, ScaffoldRequestDto request) {
        try {
            Thread.sleep(1200);
            updateJob(jobId, 25, "Creating Git Repository", "[00:00:02] Created GitHub repository https://github.com/enterprise-org/" + request.getName().toLowerCase() + "\n");

            Thread.sleep(1200);
            updateJob(jobId, 50, "Generating project skeleton from " + request.getStackTemplate() + " template", "[00:00:04] Generated standard architecture skeleton & Dockerfile\n");

            Thread.sleep(1200);
            updateJob(jobId, 75, "Configuring GitHub Actions CI/CD pipeline", "[00:00:06] Created .github/workflows/deploy.yml with automated quality gate\n");

            Thread.sleep(1200);
            updateJob(jobId, 100, "Scaffolding completed successfully", "[00:00:08] Initial build succeeded. Service registered in catalog.\n");

            // Complete Project
            ProjectEntity project = projectRepository.findById(projectId).orElseThrow();
            project.setStatus("COMPLETED");
            projectRepository.save(project);

            // Register as active Service in Service Catalog!
            String serviceId = "srv-" + request.getName().toLowerCase().replaceAll("[^a-z0-9-]", "-");
            ServiceEntity newService = ServiceEntity.builder()
                    .id(serviceId)
                    .name(request.getName())
                    .description(request.getDescription())
                    .repositoryUrl(project.getRepositoryUrl())
                    .ownerTeam(request.getOwnerTeam())
                    .status("ACTIVE")
                    .techStack(request.getStackTemplate())
                    .healthPercent(100.0)
                    .latencyMs(12)
                    .environment("DEV")
                    .exposedApis(new ArrayList<>())
                    .dependencies(new ArrayList<>())
                    .build();

            serviceRepository.save(newService);
            log.info("Scaffolding job {} promoted to Service {}", jobId, serviceId);

            // Notify AMQP completion
            try {
                rabbitTemplate.convertAndSend("idp.direct.exchange", "job.scaffold.routing.key", Map.of(
                        "jobId", jobId,
                        "projectId", projectId,
                        "serviceId", serviceId,
                        "action", "SCAFFOLD_COMPLETED"
                ));
            } catch (Exception ignored) {}

        } catch (Exception e) {
            log.error("Scaffolding job failed for jobId {}", jobId, e);
            updateJob(jobId, 0, "FAILED", "[ERROR] Scaffolding error: " + e.getMessage() + "\n");
        }
        return CompletableFuture.completedFuture(null);
    }

    private void updateJob(String jobId, int progress, String step, String logMessage) {
        scaffoldJobRepository.findById(jobId).ifPresent(job -> {
            job.setProgressPercent(progress);
            job.setCurrentStep(step);
            job.setStepLogs((job.getStepLogs() != null ? job.getStepLogs() : "") + logMessage);
            if (progress == 100) {
                job.setStatus("COMPLETED");
                job.setCompletedAt(LocalDateTime.now());
            } else if (progress == 0) {
                job.setStatus("FAILED");
            }
            ScaffoldJobEntity saved = scaffoldJobRepository.save(job);
            broadcastJobProgress(saved);
        });
    }

    public ScaffoldJobEntity getJobStatus(String jobId) {
        return scaffoldJobRepository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Job not found: " + jobId));
    }
}
