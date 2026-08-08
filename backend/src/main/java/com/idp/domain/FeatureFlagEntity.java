package com.idp.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "feature_flags")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatureFlagEntity {

    @Id
    private String id;

    @Column(name = "flag_key", nullable = false, unique = true)
    private String key;

    private String description;
    
    private boolean enabled;
    
    private int rolloutPercent; // 0 to 100

    private String serviceId;

    private String targetTeam;

    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void onSave() {
        updatedAt = LocalDateTime.now();
    }
}
