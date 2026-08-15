package com.idp.rag;

import com.idp.domain.RagChunkEntity;
import com.idp.domain.RagDocumentEntity;
import com.idp.repository.RagChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Ranks indexed chunks against a natural-language question.
 *
 * <p>Scores the corpus exactly rather than probing an approximate index. At the
 * catalogue's size this is a sub-millisecond scan over a few hundred normalised
 * vectors, and it cannot miss a relevant chunk the way an ANN probe can — see V8
 * for when that trade flips.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RagRetrievalService {

    private final RagChunkRepository chunkRepository;
    private final EmbeddingModel embeddingModel;

    /** A retrieved passage with its provenance, ready to cite. */
    public record RetrievedChunk(
            String chunkId,
            String documentId,
            String documentTitle,
            String docType,
            String sourceUrl,
            String serviceId,
            String ownerTeam,
            String content,
            double score) {
    }

    /**
     * Chunks scoring below this are lexically unrelated to the question. Answering
     * from them produces confident nonsense, so the caller is told nothing was found
     * instead.
     */
    @Value("${idp.rag.min-score:0.08}")
    private double minScore;

    @Value("${idp.rag.top-k:5}")
    private int topK;

    /**
     * @param ownerTeams when non-empty, restricts results to documents owned by one
     *                   of these teams — the retrieval half of "sources filtered to
     *                   the repos the caller can actually see". Documents with no
     *                   owning service are platform-wide and always visible.
     */
    @Transactional(readOnly = true)
    public List<RetrievedChunk> retrieve(String question, List<String> ownerTeams) {
        if (question == null || question.isBlank()) {
            return List.of();
        }

        float[] queryVector = embeddingModel.embed(question);
        List<RagChunkEntity> candidates = chunkRepository.findAllEmbeddedWithDocument();
        if (candidates.isEmpty()) {
            log.warn("[RAG] Query received but the chunk store is empty — has ingestion run?");
            return List.of();
        }

        List<RetrievedChunk> scored = new ArrayList<>();
        for (RagChunkEntity chunk : candidates) {
            RagDocumentEntity document = chunk.getDocument();
            String ownerTeam = document.getService() != null ? document.getService().getOwnerTeam() : null;

            if (!isVisible(ownerTeam, ownerTeams)) {
                continue;
            }
            // Dimension drift means the model changed after this chunk was written;
            // its vector is not comparable, so exclude it rather than score garbage.
            float[] embedding = chunk.getEmbedding();
            if (embedding == null || embedding.length != queryVector.length) {
                continue;
            }

            double score = dot(queryVector, embedding);
            if (score < minScore) {
                continue;
            }

            scored.add(new RetrievedChunk(
                    chunk.getId(),
                    document.getId(),
                    document.getTitle(),
                    document.getDocType(),
                    document.getSourceUrl(),
                    document.getService() != null ? document.getService().getId() : null,
                    ownerTeam,
                    chunk.getContent(),
                    score));
        }

        scored.sort(Comparator.comparingDouble(RetrievedChunk::score).reversed());
        return scored.size() > topK ? List.copyOf(scored.subList(0, topK)) : List.copyOf(scored);
    }

    private boolean isVisible(String ownerTeam, List<String> ownerTeams) {
        if (ownerTeams == null || ownerTeams.isEmpty() || ownerTeam == null) {
            return true;
        }
        return ownerTeams.contains(ownerTeam);
    }

    /** Both vectors are L2-normalised by contract, so the dot product is the cosine. */
    private double dot(float[] a, float[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            sum += (double) a[i] * b[i];
        }
        return sum;
    }
}
