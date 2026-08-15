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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * End-to-end check over the corpus V4 actually seeds: chunk it, embed it with the
 * shipped model, and confirm the questions the UI suggests retrieve the document
 * that answers them.
 *
 * <p>The per-component tests use synthetic text, which proves the mechanics but not
 * that the real documentation is retrievable. This is the test that fails if someone
 * rewrites a seed document into something the embedder can no longer match, or
 * retunes the relevance threshold past what the real corpus scores.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RagPipelineIntegrationTest {

    @Mock private RagChunkRepository chunkRepository;

    private final EmbeddingModel embeddingModel = new HashingEmbeddingModel();
    private final DocumentChunker chunker = new DocumentChunker();
    private RagRetrievalService retrievalService;

    /** Verbatim content of the five documents seeded by V4__..._and_partitioning.sql. */
    private static final String[][] SEED_CORPUS = {
            { "doc-1", "Payment Gateway Integration Guide & API Specs", "srv-payment", "Equipe Paiement",
              "Payment Gateway Service handles credit card charges, refunds, 3DS 2.0 verification, and multi-currency transactions. Key endpoints: POST /api/v1/payments/charge, POST /api/v1/payments/refund." },
            { "doc-2", "Product Catalog Search & Pricing Runbook", "srv-catalog", "Equipe Catalogue",
              "Product Catalog Service manages product taxonomy and dynamic pricing. Backed by OpenSearch cluster. Endpoints: GET /api/v1/products, GET /api/v1/products/{id}." },
            { "doc-3", "Omnichannel Notification Architecture & Providers", "srv-notification", "Equipe Notifications",
              "Notification Dispatcher sends transactional email, SMS, and WhatsApp alerts with fallback to Twilio and SendGrid." },
            { "doc-4", "Golden Path Scaffolding Templates Guide", "srv-backstage", "Developer Experience",
              "IDP Scaffolder generates production-ready templates in SPRING_BOOT, ANGULAR, GO, and PYTHON with automated CI/CD and K8s manifests." },
            { "doc-5", "Enterprise RBAC Matrix & ABAC Policy Manual", "srv-auth", "Security & IAM Team",
              "Two-stage authorization engine evaluating matrix RBAC roles and contextual ABAC rules (pol-001 own-team, pol-010 prod changes, pol-020 tier-1 deletion, pol-030 rollout ceiling)." },
    };

    @BeforeEach
    void setUp() {
        retrievalService = new RagRetrievalService(chunkRepository, embeddingModel);
        ReflectionTestUtils.setField(retrievalService, "minScore", 0.08);
        ReflectionTestUtils.setField(retrievalService, "topK", 5);

        // Mirrors what RagIngestionService writes, without needing a database.
        List<RagChunkEntity> chunks = new ArrayList<>();
        for (String[] row : SEED_CORPUS) {
            ServiceEntity service = ServiceEntity.builder().id(row[2]).ownerTeam(row[3]).build();
            RagDocumentEntity document = RagDocumentEntity.builder()
                    .id(row[0]).title(row[1]).docType("RUNBOOK")
                    .sourceUrl("https://techdocs.company.internal/" + row[0])
                    .service(service).content(row[4])
                    .indexedAt(LocalDateTime.now())
                    .build();

            List<String> passages = chunker.chunk(row[4]);
            for (int i = 0; i < passages.size(); i++) {
                chunks.add(RagChunkEntity.builder()
                        .id(row[0] + "-c" + i)
                        .document(document)
                        .chunkIndex(i)
                        .content(passages.get(i))
                        .embedding(embeddingModel.embed(row[1] + "\n\n" + passages.get(i)))
                        .embeddingDimension(embeddingModel.dimensions())
                        .sourceHash("hash")
                        .indexedAt(LocalDateTime.now())
                        .build());
            }
        }
        when(chunkRepository.findAllEmbeddedWithDocument()).thenReturn(chunks);
    }

    private String topDocumentFor(String question) {
        List<RagRetrievalService.RetrievedChunk> results = retrievalService.retrieve(question, List.of());
        assertThat(results).as("retrieval for '%s'", question).isNotEmpty();
        return results.get(0).documentId();
    }

    @Test
    @DisplayName("each suggested question retrieves the document that answers it")
    void suggestedQuestionsRetrieveTheRightDocument() {
        assertThat(topDocumentFor("Comment intégrer l'API de Paiement ?")).isEqualTo("doc-1");
        assertThat(topDocumentFor("Quels sont les templates de scaffolding disponibles ?")).isEqualTo("doc-4");
        assertThat(topDocumentFor("Comment fonctionne la matrice RBAC et les règles ABAC ?")).isEqualTo("doc-5");
    }

    @Test
    @DisplayName("retrieves across languages when the identifiers match")
    void retrievesEnglishQuestionsToo() {
        assertThat(topDocumentFor("How do I refund a credit card charge?")).isEqualTo("doc-1");
        assertThat(topDocumentFor("Which service sends SMS and WhatsApp notifications?")).isEqualTo("doc-3");
    }

    @Test
    @DisplayName("returns nothing for a question the corpus does not cover")
    void returnsNothingForUncoveredTopic() {
        // Shares no platform vocabulary with any indexed document. The old hardcoded
        // Copilot answered questions like this confidently from its keyword table.
        //
        // Note what this deliberately does NOT assert: a question about an unrelated
        // *policy* ("la politique de télétravail") still retrieves the ABAC policy
        // manual, because "politique" genuinely is that document's vocabulary.
        // Separating HR policy from authorization policy needs meaning, not lexical
        // overlap — see DomainLexicon on where that ceiling is.
        assertThat(retrievalService.retrieve(
                "Quel est le menu de la cantine aujourd'hui ?", List.of())).isEmpty();
    }

    @Test
    @DisplayName("scopes results to the caller's team")
    void scopesResultsToOwningTeam() {
        List<RagRetrievalService.RetrievedChunk> results =
                retrievalService.retrieve("credit card charge and refund endpoints", List.of("Equipe Notifications"));

        assertThat(results).allSatisfy(chunk ->
                assertThat(chunk.ownerTeam()).isEqualTo("Equipe Notifications"));
    }
}
