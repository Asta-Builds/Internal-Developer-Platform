package com.idp.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health probe endpoint reflecting connection statuses for backends,
 * databases (PostgreSQL/H2), and message brokers (RabbitMQ).
 */
@RestController
@RequestMapping("/api/health")
@Slf4j
public class HealthController {

    private final DataSource dataSource;
    private final org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;
    private final org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;

    public HealthController() {
        this(null, null, null);
    }

    @Autowired
    public HealthController(
            @Autowired(required = false) DataSource dataSource,
            @Autowired(required = false) org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate,
            @Autowired(required = false) org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory) {
        this.dataSource = dataSource;
        this.rabbitTemplate = rabbitTemplate;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> components = new LinkedHashMap<>();
        boolean allUp = true;

        // 1. Database connection status
        if (dataSource != null) {
            try (Connection conn = dataSource.getConnection()) {
                boolean valid = conn.isValid(2);
                components.put("database", Map.of(
                        "status", valid ? "UP" : "DOWN",
                        "type", conn.getMetaData().getDatabaseProductName()
                ));
                if (!valid) allUp = false;
            } catch (Exception e) {
                components.put("database", Map.of("status", "DOWN", "error", e.getMessage()));
                allUp = false;
            }
        } else {
            components.put("database", Map.of("status", "UP", "details", "standalone/mock"));
        }

        // 2. RabbitMQ broker connection status
        if (rabbitTemplate != null && rabbitTemplate.getConnectionFactory() != null) {
            try {
                var conn = rabbitTemplate.getConnectionFactory().createConnection();
                boolean isOpen = conn.isOpen();
                conn.close();
                components.put("messageBroker", Map.of(
                        "status", isOpen ? "UP" : "DOWN",
                        "broker", "RabbitMQ"
                ));
                if (!isOpen) allUp = false;
            } catch (Exception e) {
                components.put("messageBroker", Map.of("status", "DOWN", "error", e.getMessage()));
            }
        } else {
            components.put("messageBroker", Map.of("status", "UP", "details", "standalone/mock"));
        }

        // 3. Redis / Cache layer status
        if (redisConnectionFactory != null) {
            try {
                var conn = redisConnectionFactory.getConnection();
                String ping = conn.ping();
                conn.close();
                components.put("redis", Map.of(
                        "status", "PONG".equalsIgnoreCase(ping) ? "UP" : "DEGRADED",
                        "response", String.valueOf(ping)
                ));
            } catch (Exception e) {
                components.put("redis", Map.of("status", "DEGRADED", "fallback", "Caffeine active"));
            }
        } else {
            components.put("redis", Map.of("status", "UP", "type", "Caffeine Cache Manager"));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", allUp ? "UP" : "DEGRADED");
        response.put("service", "idp-backend");
        response.put("timestamp", System.currentTimeMillis());
        response.put("components", components);

        return ResponseEntity.ok(response);
    }
}
