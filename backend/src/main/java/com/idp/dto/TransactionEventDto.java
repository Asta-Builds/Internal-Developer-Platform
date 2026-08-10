package com.idp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEventDto implements Serializable {
    private String transactionId;
    private String customerId;
    private BigDecimal amount;
    private String currency;
    private String serviceId;
    private String merchantCategory;
    private String ipAddress;
    private String deviceFingerprint;
    private long timestamp;
}
