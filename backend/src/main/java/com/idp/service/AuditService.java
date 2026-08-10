package com.idp.service;

import com.idp.domain.AuditLogEntryEntity;
import com.idp.domain.Role;
import com.idp.domain.UserEntity;
import com.idp.repository.AuditLogRepository;
import com.idp.repository.UserRepository;
import com.idp.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Transactional
    public void logAction(String actorId, String action, String targetId, String details) {
        AuditLogEntryEntity entry = AuditLogEntryEntity.builder()
                .actorId(actorId != null ? actorId : "system")
                .action(action)
                .targetId(targetId)
                .details(details)
                .build();
        auditLogRepository.save(entry);
        log.info("[AUDIT] Actor '{}' executed '{}' on target '{}': {}", actorId, action, targetId, details);
    }

    /** Unfiltered access, for internal callers that have already authorized the read. */
    public List<AuditLogEntryEntity> getAuditLogs() {
        return auditLogRepository.findAllByOrderByTimestampDesc();
    }

    /**
     * Row-level scoping of the audit trail: ADMIN reads everything, anyone else sees
     * only entries produced by members of their own team.
     */
    public List<AuditLogEntryEntity> getAuditLogsFor(AuthenticatedUser actor) {
        List<AuditLogEntryEntity> all = auditLogRepository.findAllByOrderByTimestampDesc();

        if (actor == null) {
            return List.of();
        }
        if (actor.getRole() == Role.ADMIN) {
            return all;
        }
        if (actor.getTeam() == null) {
            // No team means no basis for scoping; fall back to the actor's own entries.
            return all.stream()
                    .filter(entry -> referencesUser(entry, actor.getId()))
                    .collect(Collectors.toList());
        }

        Set<String> teammateIds = userRepository.findByTeam(actor.getTeam()).stream()
                .map(UserEntity::getId)
                .collect(Collectors.toSet());

        return all.stream()
                .filter(entry -> teammateIds.stream().anyMatch(id -> referencesUser(entry, id)))
                .collect(Collectors.toList());
    }

    /**
     * Actor ids are stored as {@code "usr-2 (alice)"}, so match on the id prefix rather
     * than on equality.
     */
    private boolean referencesUser(AuditLogEntryEntity entry, String userId) {
        String actorId = entry.getActorId();
        return actorId != null && userId != null
                && (actorId.equals(userId) || actorId.startsWith(userId + " "));
    }
}
