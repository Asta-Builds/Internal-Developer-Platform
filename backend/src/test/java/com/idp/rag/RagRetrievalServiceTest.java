package com.idp.rag;

import com.idp.domain.RagChunkEntity;
import com.idp.domain.RagDocumentEntity;
import com.idp.domain.ServiceEntity;
import com.idp.repository.RagChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagRetrievalServiceTest {

    @Mock private RagChunkRepository chunkRepository;

    private final EmbeddingModel embeddingModel = new HashingEmbeddingModel();
    private RagRetrievalService service;

    @BeforeEach
    void setUp() {
        service = new RagRetrievalService(chunkRepository, embeddingModel);
        ReflectionTestUtils.setField(service, "minScore", 0.08);
        ReflectionTestUtils.setField(service, "topK", 5);
    }

    private RagChunkEntity chunk(String id, String content, String serviceId, String ownerTeam) {
        ServiceEntity service = serviceId == null ? null
                : ServiceEntity.builder().id(serviceId).ownerTeam(ownerTeam).build();
        RagDocumentEntity document = RagDocumentEntity.builder()
                .id("doc-" + id)
                .title("Doc " + id)
                .docType("RUNBOOK")
                .sourceUrl("https://techdocs.internal/" + id)
                .service(service)
                .content(content)
                .build();
        return RagChunkEntity.builder()
                .id(id)
                .document(document)
                .chunkIndex(0)
                .content(content)
                .embedding(embeddingModel.embed(content))
                .embeddingDimension(embeddingModel.dimensions())
                .sourceHash("hash")
                .build();
    }

    @Test
    @DisplayName("ranks the passage that answers the question first")
    void ranksMostRelevantFirst() {
        when(chunkRepository.findAllEmbeddedWithDocument()).thenReturn(List.of(
                chunk("grafana", "Grafana dashboards visualise Prometheus metrics and pod memory.", "srv-grafana", "Observability Team"),
                chunk("payment", "The payment gateway charges credit cards and issues refunds via the charge endpoint.", "srv-payment", "Equipe Paiement")));

        List<RagRetrievalService.RetrievedChunk> results =
                service.retrieve("How do I refund a credit card charge?", List.of());

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).documentId()).isEqualTo("doc-payment");
        assertThat(results.get(0).score()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("returns nothing when no chunk clears the relevance threshold")
    void filtersOutIrrelevantChunks() {
        when(chunkRepository.findAllEmbeddedWithDocument()).thenReturn(List.of(
                chunk("payment", "The payment gateway charges credit cards and issues refunds.", "srv-payment", "Equipe Paiement")));

        // Answering this from a payments runbook would be confident nonsense.
        assertThat(service.retrieve("zzzz qqqq xxxx unrelated gibberish", List.of())).isEmpty();
    }

    @Test
    @DisplayName("hides documents owned by another team")
    void filtersByOwnerTeam() {
        when(chunkRepository.findAllEmbeddedWithDocument()).thenReturn(List.of(
                chunk("payment", "The payment gateway charges credit cards and issues refunds.", "srv-payment", "Equipe Paiement")));

        List<RagRetrievalService.RetrievedChunk> results =
                service.retrieve("credit card refunds", List.of("Observability Team"));

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("keeps platform-wide documents that belong to no service")
    void keepsUnownedDocuments() {
        when(chunkRepository.findAllEmbeddedWithDocument()).thenReturn(List.of(
                chunk("golden", "The scaffolder generates Spring Boot and Angular templates with CI/CD.", null, null)));

        List<RagRetrievalService.RetrievedChunk> results =
                service.retrieve("scaffolder templates", List.of("Equipe Paiement"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).ownerTeam()).isNull();
    }

    @Test
    @DisplayName("skips chunks whose embedding was written by a different model")
    void skipsDimensionMismatch() {
        RagChunkEntity stale = chunk("stale", "The payment gateway charges credit cards.", "srv-payment", "Equipe Paiement");
        // Simulates an embedding model swap without a forced re-index.
        stale.setEmbedding(new float[] { 0.1f, 0.9f });
        stale.setEmbeddingDimension(2);
        when(chunkRepository.findAllEmbeddedWithDocument()).thenReturn(List.of(stale));

        assertThat(service.retrieve("credit card charges", List.of())).isEmpty();
    }

    @Test
    @DisplayName("returns nothing for a blank question without touching the store")
    void ignoresBlankQuestion() {
        assertThat(service.retrieve("  ", List.of())).isEmpty();
    }
}
