package com.idp.service;

import com.idp.dto.TransactionEventDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * RabbitMQ producer for the fraud-analysis pipeline.
 */
@ExtendWith(MockitoExtension.class)
class EventPublisherServiceTest {

    @Mock private RabbitTemplate rabbitTemplate;

    private EventPublisherService service;

    @BeforeEach
    void setUp() {
        service = new EventPublisherService(rabbitTemplate);
        // Defaults come from @Value; drive them through the field to keep the test
        // free of the Spring context.
        setField("exchange", "idp.direct.exchange");
        setField("routingKey", "fraud.analysis.routing.key");
    }

    private void setField(String name, Object value) {
        try {
            var field = EventPublisherService.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(service, value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private TransactionEventDto transaction() {
        return TransactionEventDto.builder()
                .transactionId("txn-1")
                .customerId("cust-1")
                .amount(new BigDecimal("42.00"))
                .currency("EUR")
                .build();
    }

    @Test
    @DisplayName("publishes the transaction to the analysis exchange")
    void publishesTransaction() {
        service.publishTransactionForFraudAnalysis(transaction());

        verify(rabbitTemplate).convertAndSend("idp.direct.exchange", "fraud.analysis.routing.key",
                transaction());
    }

    @Test
    @DisplayName("a broker outage is tolerated")
    void toleratesBrokerOutage() {
        doThrow(new RuntimeException("down")).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(Object.class));

        service.publishTransactionForFraudAnalysis(transaction());
    }

    @Test
    @DisplayName("publishes audit payloads on the audit routing key")
    void publishesAuditEvent() {
        service.publishAuditEvent(Map.of("action", "X"));

        verify(rabbitTemplate).convertAndSend("idp.direct.exchange", "audit.event.routing.key",
                Map.of("action", "X"));
    }
}
