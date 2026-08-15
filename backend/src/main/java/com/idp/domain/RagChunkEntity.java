package com.idp.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One retrievable passage of a {@link RagDocumentEntity}, with its embedding.
 *
 * <p>Chunks — not whole documents — are the unit of retrieval: a runbook answers a
 * narrow question in one paragraph, and embedding the whole file dilutes that
 * paragraph's signal across everything else in it.
 */
@Entity
@Table(name = "rag_chunks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RagChunkEntity {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private RagDocumentEntity document;

    /** Position within the parent document; unique per document (see V8). */
    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * L2-normalised embedding. Stored as a comma-separated float list rather than a
     * pgvector column so the migration chain still applies to H2 — see V8 for the
     * full reasoning and the swap path.
     */
    @Convert(converter = FloatVectorConverter.class)
    @Column(columnDefinition = "TEXT")
    private float[] embedding;

    @Column(name = "embedding_dimension")
    private Integer embeddingDimension;

    /** SHA-256 of the parent document's content when this chunk was produced. */
    @Column(name = "source_hash", nullable = false, length = 64)
    private String sourceHash;

    @Column(name = "indexed_at", nullable = false)
    private LocalDateTime indexedAt;
}
