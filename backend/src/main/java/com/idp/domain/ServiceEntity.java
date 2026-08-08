package com.idp.domain;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "services")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    private String description;
    private String repositoryUrl;
    private String ownerTeam;
    private String status; // ACTIVE, DEPRECATED, SCAFFOLDING
    private String techStack; // SPRING_BOOT, ANGULAR, PYTHON, GO

    @Builder.Default
    private Double healthPercent = 99.9;

    @Builder.Default
    private Integer latencyMs = 14;

    @Builder.Default
    private String environment = "PROD";

    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ApiEndpointEntity> exposedApis = new ArrayList<>();

    @OneToMany(mappedBy = "sourceService", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DependencyEntity> dependencies = new ArrayList<>();
}
