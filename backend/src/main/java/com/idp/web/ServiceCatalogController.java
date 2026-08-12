package com.idp.web;

import com.idp.domain.ServiceEntity;
import com.idp.repository.ServiceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service catalog: discovery, ownership metadata (owning team, contact channel,
 * documentation link), dependency mapping, registered API contracts.
 */
@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
@Tag(name = "Service Catalog", description = "Registered services with owner/contact metadata, "
        + "dependency mapping (services, external APIs, databases, queues) and API contracts.")
public class ServiceCatalogController {

    private final ServiceRepository serviceRepository;

    @GetMapping("/services")
    @PreAuthorize("hasPermission(null, 'SERVICE', 'READ')")
    @Operation(summary = "List registered services", description = "Every service in the catalog with its owning "
            + "team, contact channel, documentation link, status and runtime metadata. Filterable by tech stack, "
            + "owner team and free-text search.")
    public ResponseEntity<List<ServiceEntity>> getServices(
            @Parameter(description = "Free-text search over name, description and owner team")
            @RequestParam(required = false) String search,
            @Parameter(description = "Tech stack filter (SPRING_BOOT, GO, ANGULAR, ... or ALL)")
            @RequestParam(required = false) String techStack,
            @Parameter(description = "Owner team filter (or ALL)")
            @RequestParam(required = false) String ownerTeam) {

        List<ServiceEntity> services = serviceRepository.findAll();

        if (techStack != null && !techStack.equalsIgnoreCase("ALL")) {
            services = services.stream()
                    .filter(s -> s.getTechStack() != null && s.getTechStack().equalsIgnoreCase(techStack))
                    .collect(Collectors.toList());
        }

        if (ownerTeam != null && !ownerTeam.equalsIgnoreCase("ALL")) {
            services = services.stream()
                    .filter(s -> s.getOwnerTeam() != null && s.getOwnerTeam().equalsIgnoreCase(ownerTeam))
                    .collect(Collectors.toList());
        }

        if (search != null && !search.isBlank()) {
            String query = search.toLowerCase();
            services = services.stream()
                    .filter(s -> s.getName().toLowerCase().contains(query) ||
                            (s.getDescription() != null && s.getDescription().toLowerCase().contains(query)) ||
                            (s.getOwnerTeam() != null && s.getOwnerTeam().toLowerCase().contains(query)))
                    .collect(Collectors.toList());
        }

        return ResponseEntity.ok(services);
    }

    @GetMapping("/services/{id}")
    @PreAuthorize("hasPermission(#id, 'SERVICE', 'READ')")
    @Operation(summary = "Get a single registered service", description = "Full ownership metadata, dependency "
            + "map and registered API contracts for one service.")
    public ResponseEntity<ServiceEntity> getServiceById(@PathVariable String id) {
        return serviceRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/teams")
    @PreAuthorize("hasPermission(null, 'SERVICE', 'READ')")
    @Operation(summary = "List owning teams", description = "Distinct, sorted set of teams that own services in the catalog.")
    public ResponseEntity<List<String>> getOwnerTeams() {
        List<String> teams = serviceRepository.findAll().stream()
                .map(ServiceEntity::getOwnerTeam)
                .filter(team -> team != null && !team.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        return ResponseEntity.ok(teams);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasPermission(null, 'SERVICE', 'READ')")
    @Operation(summary = "Catalog statistics", description = "Aggregates: total services, registered API contracts, "
            + "mapped dependencies (services + external APIs + databases + queues) and owning teams.")
    public ResponseEntity<Map<String, Object>> getCatalogStats() {
        List<ServiceEntity> services = serviceRepository.findAll();

        long totalApis = services.stream().mapToLong(s -> s.getExposedApis() != null ? s.getExposedApis().size() : 0).sum();
        long totalDependencies = services.stream().mapToLong(s -> s.getDependencies() != null ? s.getDependencies().size() : 0).sum();
        long totalTeams = services.stream().map(ServiceEntity::getOwnerTeam).distinct().count();

        Map<String, Long> stacksCount = services.stream()
                .collect(Collectors.groupingBy(s -> s.getTechStack() != null ? s.getTechStack() : "UNKNOWN", Collectors.counting()));

        return ResponseEntity.ok(Map.of(
                "totalServices", services.size(),
                "totalApis", totalApis,
                "totalDependencies", totalDependencies,
                "totalOwnerTeams", totalTeams,
                "techStacks", stacksCount
        ));
    }
}
