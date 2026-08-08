package com.idp.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogMessageDto {
    private String timestamp;
    private String serviceId;
    private String logLevel; // INFO, WARN, ERROR, SUCCESS
    private String message;
    private String sourcePod;
}
