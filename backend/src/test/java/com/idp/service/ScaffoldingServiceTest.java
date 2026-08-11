package com.idp.service;

import com.idp.domain.ScaffoldJobEntity;
import com.idp.dto.ScaffoldRequestDto;
import com.idp.repository.ProjectRepository;
import com.idp.repository.ScaffoldJobRepository;
import com.idp.repository.ServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The asynchronous scaffolding engine: idempotent initiation, job tracking and the
 * SSE subscription surface.
 */
@ExtendWith(MockitoExtension.class)
class ScaffoldingServiceTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private ScaffoldJobRepository scaffoldJobRepository;
    @Mock private ServiceRepository serviceRepository;
    @Mock private AuditService auditService;
    @Mock private IdempotencyService idempotencyService;
    @Mock private RabbitTemplate rabbitTemplate;

    private ScaffoldingService service;

    @BeforeEach
    void setUp() {
        service = new ScaffoldingService(projectRepository, scaffoldJobRepository, serviceRepository,
                auditService, idempotencyService, rabbitTemplate);
    }

    private ScaffoldRequestDto request() {
        return ScaffoldRequestDto.builder()
                .name("My Payment API")
                .description("Payment backend")
                .stackTemplate("SPRING_BOOT")
                .ownerTeam("Equipe Paiement")
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
}
