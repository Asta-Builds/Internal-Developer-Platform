package com.idp.dto;

import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CopilotChatResponseDto {
    private String answer;
    private List<String> sources;
    private double confidenceScore;
    private List<String> suggestedActions;
    private long timestamp;
}
