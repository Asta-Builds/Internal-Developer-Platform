package com.idp.config;

import com.idp.rag.RagIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final RagIngestionService ragIngestionService;

    @Override
    public void run(String... args) {
        log.info("[FLYWAY] Database schema and initial seed data managed via Flyway SQL migrations (db/migration/V1, V2).");

        // Flyway seeds the documents; only the embeddings have to be computed at
        // runtime, since they depend on the configured embedding model rather than on
        // the schema. Runs once — subsequent boots see a populated chunk store and
        // return immediately.
        try {
            ragIngestionService.ingestIfEmpty();
        } catch (RuntimeException e) {
            // A failed first index leaves the Copilot answering "corpus is empty",
            // which is recoverable via POST /api/v1/rag/ingest. Never block startup.
            log.error("[RAG INGEST] Initial ingestion failed; Copilot will report an empty corpus "
                    + "until POST /api/v1/rag/ingest succeeds", e);
        }
    }
}
