package com.idp.web;

import com.idp.domain.ScaffoldJobEntity;
import com.idp.dto.ScaffoldRequestDto;
import com.idp.service.ScaffoldingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Scaffolding endpoints: initiation is 202 Accepted and streams produce SSE.
 */
@ExtendWith(MockitoExtension.class)
class ScaffoldControllerTest {

    @Mock private ScaffoldingService scaffoldingService;

    private ScaffoldController controller;

    private final ScaffoldJobEntity job = ScaffoldJobEntity.builder()
            .id("job-1").projectId("proj-1").status("RUNNING").progressPercent(10).build();

    @BeforeEach
    void setUp() {
        controller = new ScaffoldController(scaffoldingService);
    }

    @Test
    @DisplayName("initiating a scaffold returns 202 Accepted")
    void initiatesScaffold() {
        ScaffoldRequestDto request = ScaffoldRequestDto.builder().name("svc").stackTemplate("SPRING_BOOT").build();
        when(scaffoldingService.initiateScaffold(request, "idem-1")).thenReturn(job);

        var response = controller.initiateScaffold(request, "idem-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isEqualTo(job);
    }

    @Test
    @DisplayName("initiating without an idempotency key passes null through")
    void initiatesWithoutIdempotencyKey() {
        ScaffoldRequestDto request = ScaffoldRequestDto.builder().name("svc").build();
        when(scaffoldingService.initiateScaffold(request, null)).thenReturn(job);

        assertThat(controller.initiateScaffold(request, null).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    @DisplayName("reads a job status")
    void readsJobStatus() {
        when(scaffoldingService.getJobStatus("job-1")).thenReturn(job);

        assertThat(controller.getJobStatus("job-1").getBody()).isEqualTo(job);
    }

    @Test
    @DisplayName("streams job progress as SSE")
    void streamsJobProgress() {
        when(scaffoldingService.subscribeJobStream("job-1")).thenReturn(new SseEmitter());

        var response = controller.streamJobProgress("job-1");

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("exposes the four scaffold templates")
    void templates() {
        var body = controller.getAvailableTemplates().getBody();

        assertThat(body).hasSize(4);
        assertThat(body).extracting(template -> template.get("id"))
                .containsExactly("SPRING_BOOT", "ANGULAR", "GO", "PYTHON");
    }
}
