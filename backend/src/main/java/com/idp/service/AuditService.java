package com.idp.service;

import com.idp.domain.AuditLogEntryEntity;
import com.idp.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void logAction(String actorId, String action, String targetId, String details) {
        AuditLogEntryEntity entry = AuditLogEntryEntity.builder()
                .actorId(actorId != null ? actorId : "admin")
                .action(action)
                .targetId(targetId)
                .details(details)
                .build();
        auditLogRepository.save(entry);
        log.info("[AUDIT] Actor '{}' executed '{}' on target '{}': {}", actorId, action, targetId, details);
    }

    public List<AuditLogEntryEntity> getAuditLogs() {
        return auditLogRepository.findAllByOrderByTimestampDesc();
    }
}
