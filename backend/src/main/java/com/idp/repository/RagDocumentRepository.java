package com.idp.repository;

import com.idp.domain.RagDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RagDocumentRepository extends JpaRepository<RagDocumentEntity, String> {
    List<RagDocumentEntity> findByDocType(String docType);
    List<RagDocumentEntity> findByService_Id(String serviceId);
}
