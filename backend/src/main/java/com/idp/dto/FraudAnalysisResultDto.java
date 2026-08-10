package com.idp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudAnalysisResultDto implements Serializable {
    private String transactionId;
    private double riskScore;
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    private boolean blocked;
    private List<String> triggeredRules;
    private String modelVersion;
    private long inferenceLatencyMs;
    private long processedAt;
}
