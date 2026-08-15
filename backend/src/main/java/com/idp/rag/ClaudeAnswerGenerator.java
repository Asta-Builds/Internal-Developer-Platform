package com.idp.rag;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Writes the Copilot's answer from retrieved passages, citing them by number.
 *
 * <p>Generation is <em>optional</em>: with no API key configured the bean stays
 * disabled and {@link #generate} returns empty, letting the caller fall back to
 * showing the retrieved passages directly. That keeps the platform bootable and the
 * Copilot useful in the local docker-compose stack, where no key is set.
 */
@Component
@Slf4j
public class ClaudeAnswerGenerator {

    /**
     * The model is told to answer only from the numbered sources and to say so when
     * they don't cover the question. Without that, it answers from parametric
     * knowledge and produces plausible platform details that were never true here —
     * which is exactly the failure the old hardcoded Copilot had.
     */
    private static final String SYSTEM_PROMPT = """
            You are the IDP Copilot, an assistant for an internal developer platform.

            Answer using only the numbered sources provided in the user message. Cite \
            the sources you use inline as [1], [2], and so on. If the sources do not \
            contain the answer, say plainly that the documentation does not cover it \
            and name what you would need — never fill the gap from general knowledge \
            about similar platforms.

            Answer in the language the question was asked in. Be concise: lead with the \
            answer, then supporting detail. Prefer concrete specifics from the sources \
            (endpoint paths, service ids, owning teams) over generic description.""";

    /**
     * Resolution order lets a deployment set the platform-specific property while
     * still honouring the SDK's conventional environment variable in local runs.
     */
    @Value("${idp.rag.anthropic.api-key:${ANTHROPIC_API_KEY:}}")
    private String apiKey;

    @Value("${idp.rag.anthropic.model:claude-opus-5}")
    private String model;

    @Value("${idp.rag.anthropic.max-tokens:4096}")
    private long maxTokens;

    private AnthropicClient client;

    @PostConstruct
    void init() {
        if (apiKey == null || apiKey.isBlank()) {
            log.info("[RAG] No Anthropic API key configured — Copilot serves retrieved "
                    + "passages without generated answers. Set IDP_RAG_ANTHROPIC_API_KEY "
                    + "or ANTHROPIC_API_KEY to enable generation.");
            return;
        }
        client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        log.info("[RAG] Answer generation enabled using model {}", model);
    }

    public boolean isEnabled() {
        return client != null;
    }

    /**
     * @return the generated answer, or empty when generation is disabled, the model
     *         declined, or the call failed — retrieval has already succeeded by this
     *         point, so a generation failure must degrade rather than fail the request
     */
    public Optional<String> generate(String question, List<RagRetrievalService.RetrievedChunk> sources) {
        if (!isEnabled() || sources.isEmpty()) {
            return Optional.empty();
        }

        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(maxTokens)
                    .system(SYSTEM_PROMPT)
                    // Effort is a deliberate latency choice: this is a short synthesis
                    // over passages already selected, not open-ended reasoning, and the
                    // Copilot is an interactive chat widget.
                    .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build())
                    .addUserMessage(buildPrompt(question, sources))
                    .build();

            Message response = client.messages().create(params);

            String answer = response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(text -> text.text())
                    .reduce("", (a, b) -> a + b)
                    .strip();

            // Empty content means the model produced no text — a safety refusal is one
            // way that happens. Treat it the same as any other non-answer.
            return answer.isEmpty() ? Optional.empty() : Optional.of(answer);
        } catch (RuntimeException e) {
            log.warn("[RAG] Answer generation failed, falling back to retrieved passages: {}", e.toString());
            return Optional.empty();
        }
    }

    private String buildPrompt(String question, List<RagRetrievalService.RetrievedChunk> sources) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Sources:\n\n");
        for (int i = 0; i < sources.size(); i++) {
            RagRetrievalService.RetrievedChunk source = sources.get(i);
            prompt.append('[').append(i + 1).append("] ").append(source.documentTitle());
            if (source.serviceId() != null) {
                prompt.append(" (service: ").append(source.serviceId());
                if (source.ownerTeam() != null) {
                    prompt.append(", owned by ").append(source.ownerTeam());
                }
                prompt.append(')');
            }
            prompt.append('\n').append(source.content()).append("\n\n");
        }
        prompt.append("Question: ").append(question);
        return prompt.toString();
    }
}
