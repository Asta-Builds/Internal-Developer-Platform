package com.idp.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies RabbitMQConfig creates Dead-Letter Exchanges, DLQ queues, and proper x-dead-letter arguments.
 */
class RabbitMQConfigTest {

    private RabbitMQConfig config;

    @BeforeEach
    void setUp() {
        config = new RabbitMQConfig();
        ReflectionTestUtils.setField(config, "fraudAnalysisQueue", "fraud.analysis.queue");
        ReflectionTestUtils.setField(config, "fraudResultQueue", "fraud.result.queue");
        ReflectionTestUtils.setField(config, "auditStreamQueue", "audit.event.queue");
        ReflectionTestUtils.setField(config, "flagUpdatedQueue", "flag.updated.queue");
        ReflectionTestUtils.setField(config, "scaffoldJobQueue", "job.scaffold.queue");
        ReflectionTestUtils.setField(config, "exchange", "idp.direct.exchange");
        ReflectionTestUtils.setField(config, "routingKey", "fraud.analysis.routing.key");
        ReflectionTestUtils.setField(config, "deadLetterExchange", "idp.deadletter.exchange");
        ReflectionTestUtils.setField(config, "deadLetterQueue", "idp.deadletter.queue");
        ReflectionTestUtils.setField(config, "fraudAnalysisDlq", "fraud.analysis.dlq");
    }

    @Test
    @DisplayName("creates main and dead-letter exchanges")
    void exchanges() {
        DirectExchange mainExchange = config.idpExchange();
        assertThat(mainExchange.getName()).isEqualTo("idp.direct.exchange");
        assertThat(mainExchange.isDurable()).isTrue();

        DirectExchange dlx = config.idpDeadLetterExchange();
        assertThat(dlx.getName()).isEqualTo("idp.deadletter.exchange");
        assertThat(dlx.isDurable()).isTrue();
    }

    @Test
    @DisplayName("active queues configure x-dead-letter-exchange and routing arguments")
    void queuesWithDeadLettering() {
        Queue fraudQueue = config.fraudAnalysisQueue();
        assertThat(fraudQueue.getName()).isEqualTo("fraud.analysis.queue");
        assertThat(fraudQueue.getArguments().get("x-dead-letter-exchange")).isEqualTo("idp.deadletter.exchange");
        assertThat(fraudQueue.getArguments().get("x-dead-letter-routing-key")).isEqualTo("fraud.analysis.dlq.key");

        Queue auditQueue = config.auditStreamQueue();
        assertThat(auditQueue.getArguments().get("x-dead-letter-exchange")).isEqualTo("idp.deadletter.exchange");
    }

    @Test
    @DisplayName("dead-letter queues (DLQ) and bindings are properly declared")
    void dlqQueuesAndBindings() {
        Queue dlq = config.fraudAnalysisDlq();
        assertThat(dlq.getName()).isEqualTo("fraud.analysis.dlq");

        Binding binding = config.fraudAnalysisDlqBinding();
        assertThat(binding.getDestination()).isEqualTo("fraud.analysis.dlq");
        assertThat(binding.getExchange()).isEqualTo("idp.deadletter.exchange");
        assertThat(binding.getRoutingKey()).isEqualTo("fraud.analysis.dlq.key");
    }
}
