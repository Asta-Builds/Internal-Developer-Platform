package com.idp.web;

import com.idp.dto.CopilotChatRequestDto;
import com.idp.dto.CopilotChatResponseDto;
import com.idp.service.CopilotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/copilot")
@RequiredArgsConstructor
public class CopilotController {

    private final CopilotService copilotService;

    @PostMapping("/chat")
    @PreAuthorize("hasPermission(null, 'COPILOT', 'EXECUTE')")
    public ResponseEntity<CopilotChatResponseDto> chat(@RequestBody CopilotChatRequestDto request) {
        return ResponseEntity.ok(copilotService.processQuery(request));
    }

    @GetMapping("/suggested-questions")
    @PreAuthorize("hasPermission(null, 'COPILOT', 'EXECUTE')")
    public ResponseEntity<List<String>> getSuggestedQuestions() {
        return ResponseEntity.ok(copilotService.getSuggestedQuestions());
    }
}
