package com.idp.service;

import com.idp.dto.LogMessageDto;
import com.idp.dto.ServiceHealthDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ObservabilityService {

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    public ServiceHealthDto getServiceHealth(String serviceId) {
        Random rand = new Random();
        double cpu = Math.round((2.0 + rand.nextDouble() * 8.0) * 10.0) / 10.0;
        long memory = 120 + rand.nextInt(40);

        return ServiceHealthDto.builder()
                .serviceId(serviceId)
                .serviceName(serviceId.replace("srv-", "").replace("-", " ").toUpperCase())
                .status("HEALTHY")
                .cpuUsagePercent(cpu)
                .memoryUsageMb(memory)
                .uptimeSeconds(86400L + rand.nextInt(3600))
                .activePodCount(2)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public Map<String, Object> getTelemetryMetrics() {
        return Map.of(
                "totalPods", 6,
                "healthyPods", 6,
                "avgCpuUsagePercent", 4.5,
                "avgMemoryUsageMb", 145,
                "uptimePercent", 99.99,
                "prometheusScrapeInterval", "15s"
        );
    }

    public SseEmitter streamLiveLogs(String serviceId) {
        SseEmitter emitter = new SseEmitter(180_000L); // 3 minutes timeout

        ScheduledExecutorService logScheduler = Executors.newSingleThreadScheduledExecutor();
        List<String> mockLogs = List.of(
                "[PROMETHEUS] Scraping metrics endpoint GET /actuator/prometheus -> 200 OK (8ms)",
                "[K8S-WATCH] Pod status update: srv-payment-6f8d9b-x912 is RUNNING (1/1 ready)",
                "[AUDIT] Feature flag NEW_PAYMENT_FLOW_V2 evaluated -> CANARY_MATCH for user_99",
                "[CI/CD] Build pipeline #1405 artifact deployed successfully to k8s cluster",
                "[DATABASE] HikariPool-1 connection count: 10 active, 2 idle (0ms wait)"
        );

        logScheduler.scheduleAtFixedRate(() -> {
            try {
                String timeStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                String logText = mockLogs.get(new Random().nextInt(mockLogs.size()));

                LogMessageDto logMsg = LogMessageDto.builder()
                        .timestamp(timeStr)
                        .serviceId(serviceId != null ? serviceId : "all-services")
                        .logLevel("INFO")
                        .message(logText)
                        .sourcePod("pod-" + UUID.randomUUID().toString().substring(0, 5))
                        .build();

                emitter.send(SseEmitter.event().name("log").data(logMsg));
            } catch (IOException e) {
                logScheduler.shutdown();
                emitter.completeWithError(e);
            }
        }, 1, 3, TimeUnit.SECONDS);

        emitter.onCompletion(logScheduler::shutdown);
        emitter.onTimeout(logScheduler::shutdown);
        emitter.onError(throwable -> logScheduler.shutdown());

        return emitter;
    }
}
