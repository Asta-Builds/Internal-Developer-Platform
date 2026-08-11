package com.idp.service;

import com.idp.dto.FraudAnalysisResultDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The AI fraud verdict is only surfaced to the audit trail when it blocks.
 */
@ExtendWith(MockitoExtension.class)
class FraudResultConsumerTest {

    @Mock private AuditService auditService;

    private FraudResultConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new FraudResultConsumer(auditService);
    }

    @Test
    @DisplayName("a blocked verdict is written to the audit trail")
    void blockedVerdictIsAudited() {
        FraudAnalysisResultDto result = FraudAnalysisResultDto.builder()
                .transactionId("txn-1")
                .riskScore(0.87)
                .riskLevel("CRITICAL")
                .blocked(true)
                .triggeredRules(List.of("RULE_AMOUNT_EXCEEDS_5000_EUR"))
                .modelVersion("v2.1")
                .build();

        consumer.receiveFraudResult(result);

        verify(auditService).logAction(eq("SYSTEM_AI"), eq("AI_FRAUD_BLOCK"),
                eq("txn-1"), anyString());
    }

    @Test
    @DisplayName("a non-blocked verdict produces no audit entry")
    void nonBlockedVerdictSilent() {
        FraudAnalysisResultDto result = FraudAnalysisResultDto.builder()
                .transactionId("txn-2")
                .riskScore(0.10)
                .riskLevel("LOW")
                .blocked(false)
                .triggeredRules(List.of())
                .build();

        consumer.receiveFraudResult(result);

        verify(auditService, never()).logAction(anyString(), anyString(), anyString(), anyString());
    }
}
