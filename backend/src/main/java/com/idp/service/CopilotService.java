package com.idp.service;

import com.idp.domain.RagDocumentEntity;
import com.idp.dto.CopilotChatRequestDto;
import com.idp.dto.CopilotChatResponseDto;
import com.idp.rag.ClaudeAnswerGenerator;
import com.idp.rag.RagIngestionService;
import com.idp.rag.RagRetrievalService;
import com.idp.repository.RagChunkRepository;
import com.idp.repository.RagDocumentRepository;
import com.idp.security.AuthenticatedUser;
import com.idp.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The IDP Copilot: answers questions from the platform's own indexed documentation.
 *
 * <p>The pipeline is retrieve → generate → cite. Every answer is grounded in chunks
 * that were actually retrieved, and every cited source is one of them, so an answer
 * can always be traced back to the document it came from.
 *
 * <p>Both halves degrade independently. With nothing indexed, the caller is told the
 * corpus is empty rather than given a guess; with no Anthropic key configured,
 * retrieval still runs and the passages are returned directly. Neither case
 * fabricates platform detail.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CopilotService {

    private final RagDocumentRepository ragDocumentRepository;
    private final RagChunkRepository ragChunkRepository;
    private final RagRetrievalService retrievalService;
    private final ClaudeAnswerGenerator answerGenerator;
    private final RagIngestionService ingestionService;

    public CopilotChatResponseDto processQuery(CopilotChatRequestDto request) {
        String question = request != null ? request.getQuery() : null;
        if (question == null || question.isBlank()) {
            return response("Posez une question sur le catalogue, les APIs, les runbooks ou les feature flags.",
                    List.of(), 0.0, getSuggestedQuestions());
        }

        List<RagRetrievalService.RetrievedChunk> chunks = retrievalService.retrieve(question, visibleTeams());

        if (chunks.isEmpty()) {
            log.info("[RAG] No chunk passed the relevance threshold for query: {}", question);
            return response(noMatchMessage(), List.of(), 0.0, getSuggestedQuestions());
        }

        List<String> sources = citations(chunks);
        Optional<String> generated = answerGenerator.generate(question, chunks);

        return response(
                generated.orElseGet(() -> extractiveAnswer(chunks)),
                sources,
                // The top chunk's cosine score is the honest confidence signal: it is
                // how close the best-matching passage actually was, not a constant.
                chunks.get(0).score(),
                followUpActions(chunks));
    }

    /**
     * Teams whose documentation the caller may see.
     *
     * <p>An empty list means unrestricted. ADMIN and TECH_LEAD review the platform as
     * a whole, so they are not scoped; everyone else sees their own team's documents
     * plus the platform-wide ones that belong to no service.
     */
    private List<String> visibleTeams() {
        Optional<AuthenticatedUser> user = CurrentUser.get();
        if (user.isEmpty()) {
            return List.of();
        }
        AuthenticatedUser principal = user.get();
        if (principal.getRole() != null && principal.getRole().isAtLeast(com.idp.domain.Role.TECH_LEAD)) {
            return List.of();
        }
        return principal.getTeam() != null ? List.of(principal.getTeam()) : List.of();
    }

    /** Distinct cited documents, in the order they were ranked. */
    private List<String> citations(List<RagRetrievalService.RetrievedChunk> chunks) {
        Set<String> seen = new LinkedHashSet<>();
        for (RagRetrievalService.RetrievedChunk chunk : chunks) {
            String url = chunk.sourceUrl();
            seen.add(url != null && !url.isBlank()
                    ? chunk.documentTitle() + " — " + url
                    : chunk.documentTitle());
        }
        return List.copyOf(seen);
    }

    /**
     * Fallback when generation is unavailable: hand back the best passage verbatim.
     *
     * <p>Quoting rather than paraphrasing is deliberate — without a model in the loop
     * there is nothing that can safely rewrite the text, and a verbatim excerpt with
     * its source attached is still a useful answer.
     */
    private String extractiveAnswer(List<RagRetrievalService.RetrievedChunk> chunks) {
        RagRetrievalService.RetrievedChunk best = chunks.get(0);
        StringBuilder answer = new StringBuilder();
        answer.append("**").append(best.documentTitle()).append("**\n\n")
                .append(best.content());
        if (chunks.size() > 1) {
            answer.append("\n\n_").append(chunks.size() - 1)
                    .append(" autre(s) passage(s) pertinent(s) — voir les sources citées._");
        }
        if (!answerGenerator.isEnabled()) {
            answer.append("\n\n> Génération désactivée (aucune clé API configurée) : "
                    + "extrait de documentation renvoyé tel quel.");
        }
        return answer.toString();
    }

    private String noMatchMessage() {
        long indexed = ragChunkRepository.count();
        if (indexed == 0) {
            return "La base de connaissances est vide. Lancez une indexation via "
                    + "`POST /api/v1/rag/ingest` pour rendre la documentation interrogeable.";
        }
        return "Aucun passage pertinent trouvé dans la documentation indexée ("
                + indexed + " extraits). Reformulez la question, ou indexez la "
                + "documentation du service concerné.";
    }

    /** Concrete next steps drawn from what was actually retrieved. */
    private List<String> followUpActions(List<RagRetrievalService.RetrievedChunk> chunks) {
        List<String> actions = new ArrayList<>();
        Set<String> services = new LinkedHashSet<>();
        for (RagRetrievalService.RetrievedChunk chunk : chunks) {
            if (chunk.serviceId() != null) {
                services.add(chunk.serviceId());
            }
        }
        for (String serviceId : services) {
            actions.add("Voir la fiche catalogue de " + serviceId);
            if (actions.size() >= 3) {
                break;
            }
        }
        if (actions.isEmpty()) {
            actions.addAll(getSuggestedQuestions().subList(0, 2));
        }
        return actions;
    }

    private CopilotChatResponseDto response(String answer, List<String> sources,
                                            double confidence, List<String> actions) {
        return CopilotChatResponseDto.builder()
                .answer(answer)
                .sources(sources)
                .confidenceScore(confidence)
                .suggestedActions(actions)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public List<String> getSuggestedQuestions() {
        return List.of(
                "Comment intégrer l'API de Paiement ?",
                "Quels sont les templates de scaffolding disponibles ?",
                "Comment fonctionne le Canary Rollout des Feature Flags ?",
                "Quels microservices utilisent la stack GO ?"
        );
    }

    /**
     * Lists indexed documentation sources, with how much of each is actually
     * retrievable — a document with zero chunks is registered but not yet indexed,
     * and the UI should be able to tell the difference.
     */
    public List<Map<String, Object>> getIndexedSources() {
        return ragDocumentRepository.findAll().stream().map(doc -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", doc.getId());
            map.put("title", doc.getTitle());
            map.put("docType", doc.getDocType());
            map.put("sourceUrl", doc.getSourceUrl());
            map.put("serviceId", doc.getService() != null ? doc.getService().getId() : null);
            map.put("embeddingDimension", doc.getEmbeddingDimension());
            map.put("indexedAt", doc.getIndexedAt());
            map.put("chunkCount", ragChunkRepository.countByDocument_Id(doc.getId()));
            return map;
        }).toList();
    }

    /** Chunks, embeds and stores documentation so it becomes retrievable. */
    public Map<String, Object> reindexDocumentation(Map<String, Object> payload) {
        String serviceId = payload != null && payload.get("serviceId") != null
                ? String.valueOf(payload.get("serviceId"))
                : null;
        boolean force = payload != null && Boolean.parseBoolean(String.valueOf(payload.get("force")));

        RagIngestionService.IngestionReport report = ingestionService.ingest(serviceId, force);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "INGESTION_COMPLETED");
        result.put("serviceTarget", serviceId == null ? "all" : serviceId);
        result.put("documentsConsidered", report.documentsConsidered());
        result.put("documentsIndexed", report.documentsIndexed());
        result.put("documentsSkipped", report.documentsSkipped());
        result.put("chunksWritten", report.chunksWritten());
        result.put("embeddingModel", report.embeddingModel());
        result.put("vectorDimension", report.embeddingDimension());
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    /** Retained for callers that still resolve documents directly. */
    public Optional<RagDocumentEntity> findDocument(String id) {
        return ragDocumentRepository.findById(id);
    }
}
