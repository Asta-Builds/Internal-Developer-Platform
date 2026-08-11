package com.idp.service;

import com.idp.domain.ServiceEntity;
import com.idp.repository.ServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GitHub OAuth listing and catalog imports.
 */
@ExtendWith(MockitoExtension.class)
class GitHubIntegrationServiceTest {

    @Mock private ServiceRepository serviceRepository;

    private GitHubIntegrationService service;

    @BeforeEach
    void setUp() {
        service = new GitHubIntegrationService(serviceRepository);
    }

    @Test
    @DisplayName("lists the connected user repositories")
    void listsRepositories() {
        Map<String, Object> result = service.getConnectedUserRepositories("alice");

        assertThat(result.get("authenticatedUser")).isEqualTo("alice");
        assertThat(result.get("totalRepositories")).isEqualTo(4);
        @SuppressWarnings("unchecked")
        var repos = (java.util.List<Map<String, Object>>) result.get("repositories");
        assertThat(repos).extracting(repo -> repo.get("fullName"))
                .contains("alice/spring-petclinic-microservice");
    }

    @Test
    @DisplayName("imports a repository as an ACTIVE service")
    void importsRepository() {
        ServiceEntity saved = ServiceEntity.builder().id("srv-pet-clinic").build();
        when(serviceRepository.save(org.mockito.ArgumentMatchers.any(ServiceEntity.class)))
                .thenReturn(saved);

        ServiceEntity result = service.importGitHubRepository(
                "acme/pet-clinic", "pet-clinic", "A clinic", "SPRING_BOOT", "Equipe Paiement");

        assertThat(result).isSameAs(saved);
        ArgumentCaptor<ServiceEntity> captor = ArgumentCaptor.forClass(ServiceEntity.class);
        verify(serviceRepository).save(captor.capture());
        ServiceEntity built = captor.getValue();
        assertThat(built.getId()).isEqualTo("srv-pet-clinic");
        assertThat(built.getName()).isEqualTo("Pet-clinic");
        assertThat(built.getStatus()).isEqualTo("ACTIVE");
        assertThat(built.getRepositoryUrl()).isEqualTo("https://github.com/acme/pet-clinic");
        assertThat(built.getOwnerTeam()).isEqualTo("Equipe Paiement");
    }

    @Test
    @DisplayName("import applies platform defaults for missing attributes")
    void importDefaults() {
        when(serviceRepository.save(org.mockito.ArgumentMatchers.any(ServiceEntity.class)))
                .thenReturn(ServiceEntity.builder().id("srv-my-service").build());

        service.importGitHubRepository("octocat/my-service", "my-service", null, null, null);

        ArgumentCaptor<ServiceEntity> captor = ArgumentCaptor.forClass(ServiceEntity.class);
        verify(serviceRepository).save(captor.capture());
        ServiceEntity built = captor.getValue();
        assertThat(built.getOwnerTeam()).isEqualTo("Equipe Platform");
        assertThat(built.getTechStack()).isEqualTo("SPRING_BOOT");
    }
}
