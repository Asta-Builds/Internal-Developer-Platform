-- Flyway Migration V8: Chunk + embedding store backing the IDP Copilot's retrieval.
--
-- V4 created rag_documents and called itself a vector store, but never added an
-- embedding column — nothing was ever indexed, and CopilotService answered from a
-- hardcoded keyword table. This migration adds the store that makes retrieval real.
--
-- Why the embedding is TEXT rather than pgvector's vector(N):
--   1. FlywayMigrationTest applies this whole chain to H2 in PostgreSQL mode, where
--      CREATE EXTENSION vector and the vector type do not exist. A pgvector column
--      here breaks the test suite even though production runs pgvector/pgvector:pg16.
--   2. At this corpus size (single-digit documents, tens of chunks) an exact cosine
--      scan in Java is both faster and more accurate than an approximate HNSW probe;
--      HNSW starts paying off around 10^4 vectors.
-- The encoding is a compact comma-separated float list, read and written in exactly
-- one place (RagChunkEntity's converter), so moving to a real vector column is a
-- change to that converter plus a Postgres-only migration — not a rewrite of the
-- retrieval path.

CREATE TABLE IF NOT EXISTS rag_chunks (
    id VARCHAR(255) PRIMARY KEY,
    document_id VARCHAR(255) NOT NULL REFERENCES rag_documents(id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    -- L2-normalised embedding, one float per dimension. Nullable so a chunk can be
    -- persisted before its embedding is computed; retrieval skips NULL rows.
    embedding TEXT,
    embedding_dimension INT,
    -- SHA-256 of the source document's content at ingestion time. Lets re-ingestion
    -- skip documents whose text has not moved since the last run.
    source_hash VARCHAR(64) NOT NULL,
    indexed_at TIMESTAMP NOT NULL
);

-- Retrieval scans by document (to join titles/URLs for citation) and ingestion
-- deletes by document before re-inserting; both go through document_id.
CREATE INDEX IF NOT EXISTS idx_rag_chunks_document ON rag_chunks(document_id);

-- A document's chunks are ordered and unique by position, so a re-ingestion that
-- half-failed can never leave two chunks claiming the same slot.
CREATE UNIQUE INDEX IF NOT EXISTS idx_rag_chunks_doc_position ON rag_chunks(document_id, chunk_index);
