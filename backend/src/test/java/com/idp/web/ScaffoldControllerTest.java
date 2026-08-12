package com.idp.web;

import com.idp.domain.ScaffoldJobEntity;
import com.idp.dto.ScaffoldRequestDto;
import com.idp.service.ScaffoldingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Scaffolding endpoints: initiation is 202 Accepted, streams produce SSE, and artifacts can be downloaded.
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
    @DisplayName("downloads packaged artifact when present")
    void downloadsArtifact(@TempDir Path tempDir) throws IOException {
        Path artifact = tempDir.resolve("project.zip");
        Files.writeString(artifact, "dummy-zip-content");

        when(scaffoldingService.resolveArtifact("job-1")).thenReturn(Optional.of(artifact));

        var response = controller.downloadArtifact("job-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"project.zip\"");
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("downloadArtifact throws 404 NOT_FOUND when artifact is not ready")
    void downloadsArtifactNotFound() {
        when(scaffoldingService.resolveArtifact("job-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.downloadArtifact("job-1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                });
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
