package com.idp.rag;

import com.idp.domain.RagChunkEntity;
import com.idp.domain.RagDocumentEntity;
import com.idp.repository.RagChunkRepository;
import com.idp.repository.RagDocumentRepository;
import com.idp.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Chunks, embeds and persists documentation so the Copilot can retrieve it.
 *
 * <p>Ingestion is idempotent per document: a document whose content hash matches
 * what is already indexed is skipped, so re-running against an unchanged corpus is
 * cheap and safe. This is what the Git-webhook worker described in the architecture
 * notes will call once it lands — it only needs to pass the changed document ids.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RagIngestionService {

    private final RagDocumentRepository documentRepository;
    private final RagChunkRepository chunkRepository;
    private final DocumentChunker chunker;
    private final EmbeddingModel embeddingModel;
    private final AuditService auditService;

    /** Outcome of one ingestion run, surfaced by {@code POST /api/v1/rag/ingest}. */
    public record IngestionReport(
            int documentsConsidered,
            int documentsIndexed,
            int documentsSkipped,
            int chunksWritten,
            String embeddingModel,
            int embeddingDimension) {
    }

    /**
     * @param serviceId when non-null, restricts ingestion to that service's documents
     * @param force re-embeds even when the content hash is unchanged — use after
     *              swapping the embedding model, which invalidates stored vectors
     */
    @Transactional
    public IngestionReport ingest(String serviceId, boolean force) {
        List<RagDocumentEntity> documents = serviceId == null || serviceId.isBlank() || "all".equalsIgnoreCase(serviceId)
                ? documentRepository.findAll()
                : documentRepository.findByService_Id(serviceId);

        int indexed = 0;
        int skipped = 0;
        int chunksWritten = 0;

        for (RagDocumentEntity document : documents) {
            String hash = sha256(document.getContent());

            if (!force && isUpToDate(document, hash)) {
                skipped++;
                continue;
            }

            // Replace wholesale rather than diffing: chunk boundaries shift when the
            // text changes, so surviving chunks from the old revision would be
            // fragments of a document that no longer reads that way.
            chunkRepository.deleteByDocument_Id(document.getId());
            chunkRepository.flush();

            List<String> passages = chunker.chunk(document.getContent());
            List<RagChunkEntity> chunks = new ArrayList<>(passages.size());
            for (int i = 0; i < passages.size(); i++) {
                String passage = passages.get(i);
                chunks.add(RagChunkEntity.builder()
                        .id(document.getId() + "-c" + i)
                        .document(document)
                        .chunkIndex(i)
                        .content(passage)
                        // Prepending the title gives every chunk the document's subject,
                        // which a mid-document passage otherwise never states.
                        .embedding(embeddingModel.embed(document.getTitle() + "\n\n" + passage))
                        .embeddingDimension(embeddingModel.dimensions())
                        .sourceHash(hash)
                        .indexedAt(LocalDateTime.now())
                        .build());
            }
            chunkRepository.saveAll(chunks);

            document.setEmbeddingDimension(embeddingModel.dimensions());
            document.setIndexedAt(LocalDateTime.now());
            documentRepository.save(document);

            indexed++;
            chunksWritten += chunks.size();
            log.info("[RAG INGEST] Indexed '{}' ({}) into {} chunks", document.getTitle(), document.getId(), chunks.size());
        }

        IngestionReport report = new IngestionReport(
                documents.size(), indexed, skipped, chunksWritten,
                embeddingModel.name(), embeddingModel.dimensions());

        auditService.logAction("system", "RAG_INGESTION",
                serviceId == null ? "all" : serviceId,
                "Indexed " + indexed + " document(s) into " + chunksWritten + " chunks using "
                        + embeddingModel.name() + "; " + skipped + " unchanged");

        return report;
    }

    /**
     * True when this document already has chunks carrying the current content hash
     * at the current embedding dimension.
     *
     * <p>The dimension check matters: after swapping the embedding model, the hash is
     * unchanged but every stored vector is meaningless, and skipping would leave the
     * index silently corrupt.
     */
    private boolean isUpToDate(RagDocumentEntity document, String hash) {
        List<RagChunkEntity> existing = chunkRepository.findByDocument_Id(document.getId());
        if (existing.isEmpty()) {
            return false;
        }
        return existing.stream().allMatch(chunk ->
                hash.equals(chunk.getSourceHash())
                        && chunk.getEmbedding() != null
                        && Integer.valueOf(embeddingModel.dimensions()).equals(chunk.getEmbeddingDimension()));
    }

    /** Ensures a corpus exists on first boot without requiring a manual ingest call. */
    @Transactional
    public void ingestIfEmpty() {
        if (chunkRepository.count() > 0) {
            return;
        }
        log.info("[RAG INGEST] Chunk store empty — running initial ingestion");
        IngestionReport report = ingest(null, false);
        log.info("[RAG INGEST] Initial ingestion complete: {}", report);
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Optional.ofNullable(content).orElse("").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JLS for every conforming JVM.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
