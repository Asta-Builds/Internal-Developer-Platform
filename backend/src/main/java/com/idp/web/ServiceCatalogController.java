package com.idp.web;

import com.idp.domain.ServiceEntity;
import com.idp.repository.ServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ServiceCatalogController {

    private final ServiceRepository serviceRepository;

    @GetMapping("/services")
    public ResponseEntity<List<ServiceEntity>> getServices(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String techStack,
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
    public ResponseEntity<ServiceEntity> getServiceById(@PathVariable String id) {
        return serviceRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/teams")
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
