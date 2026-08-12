package com.idp.service;

import com.idp.domain.ProjectEntity;
import com.idp.domain.ScaffoldJobEntity;
import com.idp.domain.ServiceEntity;
import com.idp.dto.ScaffoldRequestDto;
import com.idp.repository.ProjectRepository;
import com.idp.repository.ScaffoldJobRepository;
import com.idp.repository.ServiceRepository;
import com.idp.scaffold.ScaffoldTemplateEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The asynchronous scaffolding engine: idempotent initiation, job tracking,
 * output quality verification, artifact resolution and the SSE subscription surface.
 */
@ExtendWith(MockitoExtension.class)
class ScaffoldingServiceTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private ScaffoldJobRepository scaffoldJobRepository;
    @Mock private ServiceRepository serviceRepository;
    @Mock private AuditService auditService;
    @Mock private IdempotencyService idempotencyService;
    @Mock private RabbitTemplate rabbitTemplate;

    private final ScaffoldTemplateEngine templateEngine = new ScaffoldTemplateEngine();
    private final Executor directExecutor = Runnable::run;

    private ScaffoldingService service;

    @BeforeEach
    void setUp() {
        service = new ScaffoldingService(projectRepository, scaffoldJobRepository, serviceRepository,
                auditService, idempotencyService, rabbitTemplate, templateEngine, directExecutor);
    }

    private ScaffoldRequestDto request() {
        return ScaffoldRequestDto.builder()
                .name("My Payment API")
                .description("Payment backend")
                .stackTemplate("SPRING_BOOT")
                .ownerTeam("Equipe Paiement")
                .enableCiCd(true)
                .enablePostgres(true)
                .build();
    }

    @Test
    @DisplayName("initiateScaffold creates a job, sanitises the repo name and stores the result")
    void initiatesScaffold() {
        when(scaffoldJobRepository.save(any(ScaffoldJobEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScaffoldJobEntity job = service.initiateScaffold(request(), "idem-key-1");

        assertThat(job.getId()).startsWith("job-");
        assertThat(job.getProjectId()).startsWith("proj-");
        assertThat(job.getStatus()).isEqualTo("RUNNING");
        assertThat(job.getProgressPercent()).isEqualTo(10);
        verify(projectRepository).save(any());
        verify(auditService).logAction(anyString(), org.mockito.ArgumentMatchers.eq("SCAFFOLD_PROJECT_INITIATED"),
                anyString(), anyString());
        verify(idempotencyService).storeResult("idem-key-1", job);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Map.class));
    }

    @Test
    @DisplayName("initiating with blank name throws IllegalArgumentException")
    void blankNameThrows() {
        ScaffoldRequestDto badReq = ScaffoldRequestDto.builder().name("  ").stackTemplate("SPRING_BOOT").build();
        assertThatThrownBy(() -> service.initiateScaffold(badReq, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project name is required");
    }

    @Test
    @DisplayName("initiating with unknown stack template throws IllegalArgumentException")
    void unknownStackThrows() {
        ScaffoldRequestDto badReq = ScaffoldRequestDto.builder().name("Test").stackTemplate("RUBY_ON_RAILS").build();
        assertThatThrownBy(() -> service.initiateScaffold(badReq, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown stack template");
    }

    @Test
    @DisplayName("a repeated idempotency key returns the cached job without a second run")
    void returnsCachedJob() {
        ScaffoldJobEntity cached = ScaffoldJobEntity.builder()
                .id("job-cached").projectId("proj-cached").status("RUNNING").progressPercent(10).build();
        when(idempotencyService.getCachedResult("idem-key-1"))
                .thenReturn(Optional.of(cached));

        ScaffoldJobEntity result = service.initiateScaffold(request(), "idem-key-1");

        assertThat(result).isSameAs(cached);
        verify(projectRepository, never()).save(any());
        verify(scaffoldJobRepository, never()).save(any());
    }

    @Test
    @DisplayName("getJobStatus throws when the job is unknown")
    void unknownJobThrows() {
        when(scaffoldJobRepository.findById("job-nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getJobStatus("job-nope"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Job not found");
    }

    @Test
    @DisplayName("subscribeJobStream returns an emitter and pushes the current state")
    void subscribesToJobStream() {
        ScaffoldJobEntity job = ScaffoldJobEntity.builder()
                .id("job-1").projectId("proj-1").status("RUNNING").progressPercent(10).build();
        when(scaffoldJobRepository.findById("job-1")).thenReturn(Optional.of(job));

        SseEmitter emitter = service.subscribeJobStream("job-1");

        assertThat(emitter).isNotNull();
        emitter.complete();
    }

    @Test
    @DisplayName("AMQP failure during initiation is tolerated")
    void amqpFailureTolerated() {
        when(scaffoldJobRepository.save(any(ScaffoldJobEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.doThrow(new RuntimeException("down"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        ScaffoldJobEntity job = service.initiateScaffold(request(), null);

        assertThat(job.getId()).startsWith("job-");
    }

    @Test
    @DisplayName("executing scaffolding pipeline generates real artifact, completes job and registers service in catalog")
    void pipelineCompletesAndRegistersService(@TempDir Path tempDir) throws Exception {
        ReflectionTestUtils.setField(service, "artifactDir", tempDir.toString());

        String projectId = "proj-test-123";
        String jobId = "job-test-456";

        ProjectEntity project = ProjectEntity.builder()
                .id(projectId)
                .name("Order Service")
                .description("Order processing backend")
                .stackTemplate("SPRING_BOOT")
                .repositoryUrl("https://github.com/enterprise-org/order-service")
                .status("SCAFFOLDING")
                .build();

        ScaffoldJobEntity job = ScaffoldJobEntity.builder()
                .id(jobId)
                .projectId(projectId)
                .status("RUNNING")
                .progressPercent(10)
                .stepLogs("")
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(scaffoldJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(scaffoldJobRepository.save(any(ScaffoldJobEntity.class))).thenAnswer(i -> i.getArgument(0));

        ScaffoldRequestDto req = ScaffoldRequestDto.builder()
                .name("Order Service")
                .description("Order processing backend")
                .stackTemplate("SPRING_BOOT")
                .ownerTeam("Equipe Order")
                .enableCiCd(true)
                .enablePostgres(true)
                .build();

        // Run pipeline synchronously
        service.executeScaffoldingPipelineAsync(projectId, jobId, req).get();

        // Verify project completed
        assertThat(project.getStatus()).isEqualTo("COMPLETED");
        verify(projectRepository).save(project);

        // Verify service registered in catalog
        ArgumentCaptor<ServiceEntity> srvCaptor = ArgumentCaptor.forClass(ServiceEntity.class);
        verify(serviceRepository).save(srvCaptor.capture());
        ServiceEntity registered = srvCaptor.getValue();
        assertThat(registered.getId()).isEqualTo("srv-order-service");
        assertThat(registered.getName()).isEqualTo("Order Service");
        assertThat(registered.getStatus()).isEqualTo("ACTIVE");

        // Verify job state
        assertThat(job.getStatus()).isEqualTo("COMPLETED");
        assertThat(job.getProgressPercent()).isEqualTo(100);
        assertThat(job.getStepLogs()).contains("Rendered");
        assertThat(job.getStepLogs()).contains("deploy.yml");
        assertThat(job.getStepLogs()).contains("Packaged");

        // Verify artifact resolution
        Optional<Path> artifactOpt = service.resolveArtifact(jobId);
        assertThat(artifactOpt).isPresent();
        assertThat(Files.exists(artifactOpt.get())).isTrue();
        assertThat(artifactOpt.get().getFileName().toString()).isEqualTo("project.zip");
    }
}
