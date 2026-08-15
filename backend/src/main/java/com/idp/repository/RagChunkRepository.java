package com.idp.repository;

import com.idp.domain.RagChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RagChunkRepository extends JpaRepository<RagChunkEntity, String> {

    /**
     * Loads every embedded chunk with its document (and that document's service)
     * already joined.
     *
     * <p>Retrieval scores the whole corpus and then cites the winners, so the
     * document title, source URL and owning team are needed for every candidate.
     * Lazy-loading them per chunk after ranking is the classic N+1 — at a few
     * hundred chunks the join is one query and the scan is microseconds.
     */
    @Query("SELECT c FROM RagChunkEntity c "
            + "JOIN FETCH c.document d LEFT JOIN FETCH d.service "
            + "WHERE c.embedding IS NOT NULL")
    List<RagChunkEntity> findAllEmbeddedWithDocument();

    List<RagChunkEntity> findByDocument_Id(String documentId);

    void deleteByDocument_Id(String documentId);

    long countByDocument_Id(String documentId);
}
