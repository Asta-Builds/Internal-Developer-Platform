package com.idp.service;

import com.idp.dto.FraudAnalysisResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class FraudResultConsumer {

    private final AuditService auditService;

    @RabbitListener(queues = "${idp.queues.fraud-result:fraud.result.queue}", autoStartup = "${idp.rabbitmq.consumer.enabled:true}")
    public void receiveFraudResult(FraudAnalysisResultDto result) {
        log.info("[RabbitMQ Consumer] Received AI Fraud Result for txn: {} | RiskScore: {} | Level: {} | Blocked: {}",
                result.getTransactionId(), result.getRiskScore(), result.getRiskLevel(), result.isBlocked());

        if (result.isBlocked()) {
            auditService.logAction(
                    "SYSTEM_AI",
                    "AI_FRAUD_BLOCK",
                    result.getTransactionId(),
                    String.format("Transaction blocked by AI model (%s) - RiskScore: %.2f - Rules: %s",
                            result.getModelVersion(), result.getRiskScore(), result.getTriggeredRules())
            );
        }
    }
}
