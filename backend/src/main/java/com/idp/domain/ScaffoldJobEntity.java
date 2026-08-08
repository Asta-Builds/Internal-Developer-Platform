package com.idp.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "scaffold_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScaffoldJobEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String projectId;

    private String status; // PENDING, RUNNING, COMPLETED, FAILED

    private int progressPercent;

    private String currentStep;

    @Column(length = 2000)
    private String stepLogs;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    @PrePersist
    public void onCreate() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
    }
}
