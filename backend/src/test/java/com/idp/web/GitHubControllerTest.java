package com.idp.web;

import com.idp.domain.ServiceEntity;
import com.idp.service.GitHubIntegrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GitHub endpoints: repository listing and catalog imports.
 */
@ExtendWith(MockitoExtension.class)
class GitHubControllerTest {

    @Mock private GitHubIntegrationService gitHubIntegrationService;

    private GitHubController controller;

    @BeforeEach
    void setUp() {
        controller = new GitHubController(gitHubIntegrationService);
    }

    @Test
    @DisplayName("lists repositories for the requested user")
    void listsRepositories() {
        Map<String, Object> repos = Map.of("totalRepositories", 4);
        when(gitHubIntegrationService.getConnectedUserRepositories("octocat")).thenReturn(repos);

        assertThat(controller.getUserRepositories("octocat").getBody()).isEqualTo(repos);
    }

    @Test
    @DisplayName("imports a repository from the payload")
    void importsRepository() {
        ServiceEntity imported = ServiceEntity.builder().id("srv-pet-clinic").build();
        when(gitHubIntegrationService.importGitHubRepository("acme/pet-clinic", "pet-clinic",
                "A clinic", "SPRING_BOOT", "Equipe Paiement")).thenReturn(imported);

        var body = controller.importRepository(Map.of(
                "fullName", "acme/pet-clinic",
                "name", "pet-clinic",
                "description", "A clinic",
                "techStack", "SPRING_BOOT",
                "ownerTeam", "Equipe Paiement")).getBody();

        assertThat(body).isEqualTo(imported);
    }

    @Test
    @DisplayName("import applies the default payload values")
    void importDefaults() {
        when(gitHubIntegrationService.importGitHubRepository("octocat/my-service", "my-service",
                "Imported from GitHub", "SPRING_BOOT", "Equipe Platform"))
                .thenReturn(ServiceEntity.builder().id("srv-my-service").build());

        controller.importRepository(Map.of());

        verify(gitHubIntegrationService).importGitHubRepository("octocat/my-service", "my-service",
                "Imported from GitHub", "SPRING_BOOT", "Equipe Platform");
    }
}
