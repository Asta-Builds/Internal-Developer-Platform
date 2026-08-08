package com.idp.repository;

import com.idp.domain.AuditLogEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntryEntity, String> {
    List<AuditLogEntryEntity> findAllByOrderByTimestampDesc();
}
