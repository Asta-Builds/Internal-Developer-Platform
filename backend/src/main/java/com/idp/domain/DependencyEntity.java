package com.idp.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "dependencies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DependencyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String targetServiceId;
    private String type; // REST, GRPC, DB, KAFKA

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_service_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private ServiceEntity sourceService;
}
