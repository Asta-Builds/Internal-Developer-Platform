package com.idp.service;

import com.idp.dto.TransactionEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventPublisherService {

    private final RabbitTemplate rabbitTemplate;

    @Value("${idp.queues.exchange:idp.direct.exchange}")
    private String exchange;

    @Value("${idp.queues.routing-key:fraud.analysis.routing.key}")
    private String routingKey;

    public void publishTransactionForFraudAnalysis(TransactionEventDto event) {
        log.info("[RabbitMQ Producer] Publishing transaction {} for AI fraud scoring to exchange {} with routingKey {}",
                event.getTransactionId(), exchange, routingKey);
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, event);
        } catch (Exception e) {
            log.warn("[RabbitMQ Producer Fallback] Unable to publish to RabbitMQ broker: {}", e.getMessage());
        }
    }

    public void publishAuditEvent(Map<String, Object> auditPayload) {
        try {
            rabbitTemplate.convertAndSend(exchange, "audit.event.routing.key", auditPayload);
        } catch (Exception e) {
            log.debug("[RabbitMQ Audit Stream] Offline fallback mode active");
        }
    }
}
