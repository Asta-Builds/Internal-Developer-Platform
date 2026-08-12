package com.idp.web;

import com.idp.dto.CopilotChatRequestDto;
import com.idp.dto.CopilotChatResponseDto;
import com.idp.service.CopilotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for IDP Copilot RAG module, pgvector knowledge base sources,
 * and SSE real-time streaming queries.
 */
@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
public class RagController {

    private final CopilotService copilotService;

    /**
     * Natural language Q&A endpoint backed by RAG knowledge base vector search.
     */
    @PostMapping("/query")
    @PreAuthorize("hasPermission(null, 'COPILOT', 'EXECUTE')")
    public ResponseEntity<CopilotChatResponseDto> query(@RequestBody CopilotChatRequestDto request) {
        return ResponseEntity.ok(copilotService.processQuery(request));
    }

    /**
     * Lists indexed documentation sources (READMEs, OpenAPI specs, runbooks, ADRs).
     */
    @GetMapping("/sources")
    @PreAuthorize("hasPermission(null, 'COPILOT', 'READ')")
    public ResponseEntity<List<Map<String, Object>>> getSources() {
        return ResponseEntity.ok(copilotService.getIndexedSources());
    }

    /**
     * Triggers document re-indexing and embedding refresh.
     */
    @PostMapping("/ingest")
    @PreAuthorize("hasPermission(null, 'COPILOT', 'INGEST') or hasPermission(null, 'COPILOT', 'EXECUTE')")
    public ResponseEntity<Map<String, Object>> ingest(@RequestBody(required = false) Map<String, Object> payload) {
        return ResponseEntity.ok(copilotService.reindexDocumentation(payload));
    }

    /**
     * Real-time Server-Sent Events (SSE) streaming endpoint for conversational Copilot answers.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasPermission(null, 'COPILOT', 'EXECUTE')")
    public SseEmitter streamQuery(@RequestParam(defaultValue = "how do I integrate payment?") String q) {
        SseEmitter emitter = new SseEmitter(60_000L);
        CompletableFuture.runAsync(() -> {
            try {
                CopilotChatResponseDto response = copilotService.processQuery(
                        CopilotChatRequestDto.builder().query(q).build()
                );

                String[] words = response.getAnswer().split(" ");
                for (String word : words) {
                    emitter.send(SseEmitter.event().name("token").data(word + " "));
                    Thread.sleep(25);
                }

                // Send final structured metadata payload (sources and actions)
                emitter.send(SseEmitter.event().name("complete").data(response));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }
}
