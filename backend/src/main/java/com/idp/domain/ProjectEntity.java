package com.idp.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    private String description;
    private String stackTemplate;
    private String ownerTeam;
    private String repositoryName;
    private String repositoryUrl;

    private String status; // CREATED, SCAFFOLDING, COMPLETED, FAILED

    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
