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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScaffoldingService {

    private final ProjectRepository projectRepository;
    private final ScaffoldJobRepository scaffoldJobRepository;
    private final ServiceRepository serviceRepository;
    private final AuditService auditService;

    @Transactional
    public ScaffoldJobEntity initiateScaffold(ScaffoldRequestDto request) {
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
                .repositoryUrl("https://github.com/org/" + repoName)
                .status("SCAFFOLDING")
                .build();

        projectRepository.save(project);

        ScaffoldJobEntity job = ScaffoldJobEntity.builder()
                .id(jobId)
                .projectId(projectId)
                .status("RUNNING")
                .progressPercent(10)
                .currentStep("Initializing scaffolding workspace")
                .stepLogs("[00:00:01] Scaffold request received for template: " + request.getStackTemplate() + "\n")
                .build();

        ScaffoldJobEntity savedJob = scaffoldJobRepository.save(job);

        // Trigger async execution
        executeScaffoldingPipelineAsync(projectId, jobId, request);

        return savedJob;
    }

    @Async
    public CompletableFuture<Void> executeScaffoldingPipelineAsync(String projectId, String jobId, ScaffoldRequestDto request) {
        try {
            Thread.sleep(1500);
            updateJob(jobId, 25, "Creating Git Repository", "[00:00:02] Created Git repository https://github.com/org/" + request.getName().toLowerCase() + "\n");

            Thread.sleep(1500);
            updateJob(jobId, 50, "Generating project skeleton from " + request.getStackTemplate() + " template", "[00:00:04] Generated project skeleton structure\n");

            Thread.sleep(1500);
            updateJob(jobId, 75, "Configuring GitHub Actions CI/CD pipeline", "[00:00:06] Created .github/workflows/ci.yml with build & test steps\n");

            Thread.sleep(1500);
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
                    .exposedApis(new ArrayList<>())
                    .dependencies(new ArrayList<>())
                    .build();

            serviceRepository.save(newService);
            log.info("Scaffolding job {} completed and promoted to Service {}", jobId, serviceId);

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
            scaffoldJobRepository.save(job);
        });
    }

    public ScaffoldJobEntity getJobStatus(String jobId) {
        return scaffoldJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));
    }
}
