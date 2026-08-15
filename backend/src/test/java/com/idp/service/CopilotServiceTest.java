package com.idp.service;

import com.idp.dto.CopilotChatRequestDto;
import com.idp.dto.CopilotChatResponseDto;
import com.idp.rag.ClaudeAnswerGenerator;
import com.idp.rag.RagIngestionService;
import com.idp.rag.RagRetrievalService;
import com.idp.repository.RagChunkRepository;
import com.idp.repository.RagDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * The Copilot's contract: answers are grounded in retrieved passages, and both the
 * retrieval and generation halves degrade without inventing platform detail.
 */
@ExtendWith(MockitoExtension.class)
class CopilotServiceTest {

    @Mock private RagDocumentRepository documentRepository;
    @Mock private RagChunkRepository chunkRepository;
    @Mock private RagRetrievalService retrievalService;
    @Mock private ClaudeAnswerGenerator answerGenerator;
    @Mock private RagIngestionService ingestionService;

    private CopilotService service;

    @BeforeEach
    void setUp() {
        service = new CopilotService(documentRepository, chunkRepository,
                retrievalService, answerGenerator, ingestionService);
    }

    private RagRetrievalService.RetrievedChunk chunk(String title, String content, double score) {
        return new RagRetrievalService.RetrievedChunk(
                "c1", "doc-1", title, "RUNBOOK",
                "https://techdocs.internal/payments", "srv-payment", "Equipe Paiement",
                content, score);
    }

    @Test
    @DisplayName("answers from the generated text and cites the retrieved sources")
    void answersFromGeneratedText() {
        var retrieved = List.of(chunk("Payment Runbook", "Refunds go through POST /api/v1/payments/refund.", 0.62));
        when(retrievalService.retrieve(anyString(), anyList())).thenReturn(retrieved);
        when(answerGenerator.generate(anyString(), any())).thenReturn(Optional.of("Use POST /api/v1/payments/refund [1]."));

        CopilotChatResponseDto response = service.processQuery(
                CopilotChatRequestDto.builder().query("How do I refund?").build());

        assertThat(response.getAnswer()).isEqualTo("Use POST /api/v1/payments/refund [1].");
        assertThat(response.getSources()).containsExactly("Payment Runbook — https://techdocs.internal/payments");
        // Confidence is the top chunk's actual similarity, not a constant.
        assertThat(response.getConfidenceScore()).isEqualTo(0.62);
        assertThat(response.getSuggestedActions()).contains("Voir la fiche catalogue de srv-payment");
    }

    @Test
    @DisplayName("falls back to the retrieved passage when generation is unavailable")
    void fallsBackToExtractiveAnswer() {
        var retrieved = List.of(chunk("Payment Runbook", "Refunds go through POST /api/v1/payments/refund.", 0.51));
        when(retrievalService.retrieve(anyString(), anyList())).thenReturn(retrieved);
        when(answerGenerator.generate(anyString(), any())).thenReturn(Optional.empty());
        when(answerGenerator.isEnabled()).thenReturn(false);

        CopilotChatResponseDto response = service.processQuery(
                CopilotChatRequestDto.builder().query("How do I refund?").build());

        // The passage is quoted verbatim — nothing paraphrases it without a model.
        assertThat(response.getAnswer()).contains("POST /api/v1/payments/refund");
        assertThat(response.getAnswer()).contains("Génération désactivée");
        assertThat(response.getSources()).hasSize(1);
    }

    @Test
    @DisplayName("says the corpus is empty rather than guessing when nothing is indexed")
    void reportsEmptyCorpus() {
        when(retrievalService.retrieve(anyString(), anyList())).thenReturn(List.of());
        when(chunkRepository.count()).thenReturn(0L);

        CopilotChatResponseDto response = service.processQuery(
                CopilotChatRequestDto.builder().query("How do I refund?").build());

        assertThat(response.getAnswer()).contains("base de connaissances est vide");
        assertThat(response.getSources()).isEmpty();
        assertThat(response.getConfidenceScore()).isZero();
    }

    @Test
    @DisplayName("reports no relevant match when the corpus is indexed but unrelated")
    void reportsNoRelevantMatch() {
        when(retrievalService.retrieve(anyString(), anyList())).thenReturn(List.of());
        when(chunkRepository.count()).thenReturn(42L);

        CopilotChatResponseDto response = service.processQuery(
                CopilotChatRequestDto.builder().query("unrelated question").build());

        assertThat(response.getAnswer()).contains("Aucun passage pertinent");
        assertThat(response.getAnswer()).contains("42");
    }

    @Test
    @DisplayName("prompts for a question instead of retrieving on blank input")
    void handlesBlankQuery() {
        CopilotChatResponseDto response = service.processQuery(
                CopilotChatRequestDto.builder().query("  ").build());

        assertThat(response.getSources()).isEmpty();
        assertThat(response.getSuggestedActions()).isNotEmpty();
    }

    @Test
    @DisplayName("reindex reports what ingestion actually did")
    void reindexReportsIngestionOutcome() {
        when(ingestionService.ingest(null, false)).thenReturn(
                new RagIngestionService.IngestionReport(5, 5, 0, 37, "local-hashing-v1", 384));

        var result = service.reindexDocumentation(null);

        assertThat(result).containsEntry("documentsIndexed", 5)
                .containsEntry("chunksWritten", 37)
                .containsEntry("embeddingModel", "local-hashing-v1")
                .containsEntry("vectorDimension", 384)
                .containsEntry("serviceTarget", "all");
    }
}
