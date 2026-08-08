package com.idp.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceHealthDto {
    private String serviceId;
    private String serviceName;
    private String status; // HEALTHY, DEGRADED, UNHEALTHY
    private double cpuUsagePercent;
    private long memoryUsageMb;
    private long uptimeSeconds;
    private int activePodCount;
    private long timestamp;
}
