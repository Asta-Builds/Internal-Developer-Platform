package com.idp.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CopilotChatRequestDto {
    private String query;
    private String userId;
    private String contextFilter;
}
