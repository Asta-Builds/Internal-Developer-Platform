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

    /** Slack channel / group, e-mail alias or on-call rotor for the owning team. */
    private String contactChannel;

    /** Link to the service's technical documentation (runbook, ADRs, API guide). */
    private String docsUrl;

    /** Link to the service's Grafana / APM monitoring dashboard. */
    private String grafanaUrl;

    /** Service Level Objective: Availability target and error budget. */
    @Builder.Default
    private String sloAvailability = "99.99% Availability";

    /** Service Level Objective: Latency percentiles (e.g. p95, p99). */
    @Builder.Default
    @Column(name = "slo_latency_p95")
    private String sloLatencyP95 = "< 50ms p95";

    /** Incident response escalation policy and on-call rotation routing. */
    @Builder.Default
    private String escalationPolicy = "Tier-1 SRE On-Call (P1 SLA: 5min)";

    /** Production Readiness Scorecard grade: GOLD, SILVER, BRONZE. */
    @Builder.Default
    private String scorecardGrade = "GOLD";

    private String status; // ACTIVE, DEPRECATED, SCAFFOLDING
    private String techStack; // SPRING_BOOT, ANGULAR, PYTHON, GO

    @Builder.Default
    private Double healthPercent = 99.9;

    @Builder.Default
    private Integer latencyMs = 14;

    @Builder.Default
    private String environment = "PROD";

    /** STANDARD or TIER_1. Tier-1 resources attract stricter ABAC gating. */
    @Builder.Default
    private String criticality = "STANDARD";

    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ApiEndpointEntity> exposedApis = new ArrayList<>();

    @OneToMany(mappedBy = "sourceService", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DependencyEntity> dependencies = new ArrayList<>();
}
