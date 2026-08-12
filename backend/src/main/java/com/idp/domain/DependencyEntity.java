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

    /**
     * Display name for dependencies on resources that are not registered services:
     * external APIs (Stripe, Twilio), databases (PostgreSQL payments_db) and
     * queues (Kafka topic payment-events). Mutually exclusive with targetServiceId.
     */
    private String targetExternal;

    /** DOWNSTREAM (default): the source calls/consumes the target. UPSTREAM: the source feeds the target. */
    @Builder.Default
    private String direction = "DOWNSTREAM";

    private String type; // REST, GRPC, DB, KAFKA

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_service_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private ServiceEntity sourceService;
}
