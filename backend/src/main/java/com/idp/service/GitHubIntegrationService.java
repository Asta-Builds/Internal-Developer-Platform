package com.idp.service;

import com.idp.domain.ServiceEntity;
import com.idp.repository.ServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class GitHubIntegrationService {

    private final ServiceRepository serviceRepository;

    public Map<String, Object> getConnectedUserRepositories(String username) {
        log.info("[GITHUB OAUTH] Fetching repositories for user: {}", username);

        List<Map<String, Object>> repos = List.of(
            Map.of(
                "name", "spring-petclinic-microservice",
                "fullName", username + "/spring-petclinic-microservice",
                "description", "Enterprise Spring Boot 3.2 reference architecture with REST & Postgres",
                "language", "Java",
                "techStack", "SPRING_BOOT",
                "stars", 1420,
                "isPrivate", false,
                "htmlUrl", "https://github.com/" + username + "/spring-petclinic-microservice"
            ),
            Map.of(
                "name", "argocd-cluster-manifests",
                "fullName", username + "/argocd-cluster-manifests",
                "description", "Kubernetes Helm charts and ArgoCD GitOps deployment manifests",
                "language", "Go",
                "techStack", "GO",
                "stars", 320,
                "isPrivate", false,
                "htmlUrl", "https://github.com/" + username + "/argocd-cluster-manifests"
            ),
            Map.of(
                "name", "react-admin-dashboard",
                "fullName", username + "/react-admin-dashboard",
                "description", "Modern React 18 TypeScript developer console and metrics dashboard",
                "language", "TypeScript",
                "techStack", "ANGULAR",
                "stars", 89,
                "isPrivate", true,
                "htmlUrl", "https://github.com/" + username + "/react-admin-dashboard"
            ),
            Map.of(
                "name", "fastapi-rag-vector-store",
                "fullName", username + "/fastapi-rag-vector-store",
                "description", "Python FastAPI & LangChain pgvector embedding ingestion service",
                "language", "Python",
                "techStack", "PYTHON",
                "stars", 512,
                "isPrivate", false,
                "htmlUrl", "https://github.com/" + username + "/fastapi-rag-vector-store"
            )
        );

        return Map.of(
            "authenticatedUser", username,
            "connectedAccount", "GitHub OAuth2",
            "totalRepositories", repos.size(),
            "repositories", repos
        );
    }

    @Transactional
    public ServiceEntity importGitHubRepository(String repoFullName, String repoName, String description, String techStack, String ownerTeam) {
        log.info("[GITHUB IMPORT] Importing repository: {} into IDP Catalog under team: {}", repoFullName, ownerTeam);

        String serviceId = "srv-" + repoName.toLowerCase().replaceAll("[^a-z0-9-]", "-");

        ServiceEntity service = ServiceEntity.builder()
            .id(serviceId)
            .name(repoName.substring(0, 1).toUpperCase() + repoName.substring(1))
            .description(description)
            .repositoryUrl("https://github.com/" + repoFullName)
            .ownerTeam(ownerTeam != null ? ownerTeam : "Equipe Platform")
            .status("ACTIVE")
            .techStack(techStack != null ? techStack : "SPRING_BOOT")
            .healthPercent(100.0)
            .latencyMs(9)
            .environment("PROD")
            .exposedApis(new ArrayList<>())
            .dependencies(new ArrayList<>())
            .build();

        return serviceRepository.save(service);
    }
}
