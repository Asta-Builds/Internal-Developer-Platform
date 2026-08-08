package com.idp.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String actorId;
    private String action;
    private String targetId;
    private String details;
    private LocalDateTime timestamp;

    @PrePersist
    public void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
