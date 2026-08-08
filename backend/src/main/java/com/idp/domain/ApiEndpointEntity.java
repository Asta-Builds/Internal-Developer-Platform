package com.idp.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "api_endpoints")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiEndpointEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String path;
    private String method;
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private ServiceEntity service;
}
