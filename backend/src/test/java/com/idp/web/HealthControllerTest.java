package com.idp.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Health probe — unauthenticated, reporting health for backends, databases, and brokers.
 */
class HealthControllerTest {

    private HealthController controller;

    @BeforeEach
    void setUp() {
        controller = new HealthController();
    }

    @Test
    @DisplayName("reports UP with the backend service name and default components")
    void healthDefault() {
        Map<String, Object> body = controller.getHealth().getBody();

        assertThat(body.get("status")).isEqualTo("UP");
        assertThat(body.get("service")).isEqualTo("idp-backend");
        assertThat(body.get("timestamp")).isInstanceOf(Long.class);
        assertThat(body.get("components")).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> components = (Map<String, Object>) body.get("components");
        assertThat(components).containsKeys("database", "messageBroker", "redis");
    }

    @Test
    @DisplayName("reflects live connection statuses when injected with healthy components")
    void healthWithLiveComponents() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        java.sql.Connection sqlConn = mock(java.sql.Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(sqlConn);
        when(sqlConn.isValid(anyInt())).thenReturn(true);
        when(sqlConn.getMetaData()).thenReturn(meta);
        when(meta.getDatabaseProductName()).thenReturn("PostgreSQL");

        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        ConnectionFactory rabbitFactory = mock(ConnectionFactory.class);
        Connection rabbitConn = mock(Connection.class);
        when(rabbitTemplate.getConnectionFactory()).thenReturn(rabbitFactory);
        when(rabbitFactory.createConnection()).thenReturn(rabbitConn);
        when(rabbitConn.isOpen()).thenReturn(true);

        RedisConnectionFactory redisFactory = mock(RedisConnectionFactory.class);
        RedisConnection redisConn = mock(RedisConnection.class);
        when(redisFactory.getConnection()).thenReturn(redisConn);
        when(redisConn.ping()).thenReturn("PONG");

        HealthController liveController = new HealthController(dataSource, rabbitTemplate, redisFactory);
        Map<String, Object> body = liveController.getHealth().getBody();

        assertThat(body.get("status")).isEqualTo("UP");
        @SuppressWarnings("unchecked")
        Map<String, Object> components = (Map<String, Object>) body.get("components");
        assertThat(components.get("database")).isEqualTo(Map.of("status", "UP", "type", "PostgreSQL"));
        assertThat(components.get("messageBroker")).isEqualTo(Map.of("status", "UP", "broker", "RabbitMQ"));
        assertThat(components.get("redis")).isEqualTo(Map.of("status", "UP", "response", "PONG"));
    }

    @Test
    @DisplayName("marks status as DEGRADED if database connectivity check fails")
    void healthWithDatabaseFailure() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection refused"));

        HealthController liveController = new HealthController(dataSource, null, null);
        Map<String, Object> body = liveController.getHealth().getBody();

        assertThat(body.get("status")).isEqualTo("DEGRADED");
        @SuppressWarnings("unchecked")
        Map<String, Object> components = (Map<String, Object>) body.get("components");
        @SuppressWarnings("unchecked")
        Map<String, Object> db = (Map<String, Object>) components.get("database");
        assertThat(db.get("status")).isEqualTo("DOWN");
    }
}
