package com.idp.web;

import com.idp.domain.ServiceEntity;
import com.idp.service.GitHubIntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/github")
@RequiredArgsConstructor
public class GitHubController {

    private final GitHubIntegrationService gitHubIntegrationService;

    @GetMapping("/repositories")
    @PreAuthorize("hasPermission(null, 'GITHUB', 'READ')")
    public ResponseEntity<Map<String, Object>> getUserRepositories(
            @RequestParam(defaultValue = "octocat") String username) {
        return ResponseEntity.ok(gitHubIntegrationService.getConnectedUserRepositories(username));
    }

    @PostMapping("/import")
    @PreAuthorize("hasPermission(null, 'GITHUB', 'CREATE')")
    public ResponseEntity<ServiceEntity> importRepository(@RequestBody Map<String, String> payload) {
        String fullName = payload.getOrDefault("fullName", "octocat/my-service");
        String name = payload.getOrDefault("name", "my-service");
        String description = payload.getOrDefault("description", "Imported from GitHub");
        String techStack = payload.getOrDefault("techStack", "SPRING_BOOT");
        String ownerTeam = payload.getOrDefault("ownerTeam", "Equipe Platform");

        ServiceEntity imported = gitHubIntegrationService.importGitHubRepository(fullName, name, description, techStack, ownerTeam);
        return ResponseEntity.ok(imported);
    }
}
