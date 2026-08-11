package com.idp.service;

import com.idp.domain.AuditLogEntryEntity;
import com.idp.domain.Role;
import com.idp.domain.UserEntity;
import com.idp.repository.AuditLogRepository;
import com.idp.repository.UserRepository;
import com.idp.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The immutable audit trail, including the team-scoped read path.
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private UserRepository userRepository;

    private AuditService service;

    private final AuditLogEntryEntity paymentEntry = AuditLogEntryEntity.builder()
            .id("e-1").actorId("usr-2 (alice)").action("FEATURE_FLAG_CREATED")
            .targetId("PAY_V2").details("x").build();
    private final AuditLogEntryEntity catalogEntry = AuditLogEntryEntity.builder()
            .id("e-2").actorId("usr-3 (bob)").action("SERVICE_CREATED")
            .targetId("srv-catalog").details("y").build();

    @BeforeEach
    void setUp() {
        service = new AuditService(auditLogRepository, userRepository);
    }

    @Test
    @DisplayName("logAction persists the entry and defaults a missing actor to system")
    void logsAction() {
        service.logAction(null, "SERVICE_DELETED", "srv-1", "deleted");

        ArgumentCaptor<AuditLogEntryEntity> captor = ArgumentCaptor.forClass(AuditLogEntryEntity.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getActorId()).isEqualTo("system");
        assertThat(captor.getValue().getAction()).isEqualTo("SERVICE_DELETED");
        assertThat(captor.getValue().getTargetId()).isEqualTo("srv-1");
    }

    @Test
    @DisplayName("getAuditLogs returns the full trail")
    void readsFullTrail() {
        when(auditLogRepository.findAllByOrderByTimestampDesc()).thenReturn(List.of(paymentEntry, catalogEntry));

        assertThat(service.getAuditLogs()).containsExactly(paymentEntry, catalogEntry);
    }

    @Test
    @DisplayName("ADMIN reads everything")
    void adminReadsEverything() {
        AuthenticatedUser admin = AuthenticatedUser.builder()
                .id("usr-1").username("root").role(Role.ADMIN).build();
        when(auditLogRepository.findAllByOrderByTimestampDesc()).thenReturn(List.of(paymentEntry, catalogEntry));

        assertThat(service.getAuditLogsFor(admin)).containsExactly(paymentEntry, catalogEntry);
    }

    @Test
    @DisplayName("a null actor sees nothing")
    void nullActorSeesNothing() {
        when(auditLogRepository.findAllByOrderByTimestampDesc()).thenReturn(List.of(paymentEntry));

        assertThat(service.getAuditLogsFor(null)).isEmpty();
    }

    @Test
    @DisplayName("non-ADMIN sees only entries of their team mates")
    void scopesToTeam() {
        AuthenticatedUser paymentDev = AuthenticatedUser.builder()
                .id("usr-4").username("charlie").role(Role.DEVELOPER).team("Equipe Paiement").build();
        when(auditLogRepository.findAllByOrderByTimestampDesc()).thenReturn(List.of(paymentEntry, catalogEntry));
        when(userRepository.findByTeam("Equipe Paiement")).thenReturn(List.of(
                UserEntity.builder().id("usr-2").username("alice").build(),
                UserEntity.builder().id("usr-4").username("charlie").build()));

        List<AuditLogEntryEntity> visible = service.getAuditLogsFor(paymentDev);

        assertThat(visible).containsExactly(paymentEntry);
    }

    @Test
    @DisplayName("a team-less actor falls back to their own entries")
    void fallsBackToOwnEntries() {
        AuthenticatedUser loner = AuthenticatedUser.builder()
                .id("usr-9").username("zoe").role(Role.DEVELOPER).team(null).build();
        AuditLogEntryEntity own = AuditLogEntryEntity.builder()
                .id("e-3").actorId("usr-9 (zoe)").action("X").targetId("t").details("d").build();
        when(auditLogRepository.findAllByOrderByTimestampDesc())
                .thenReturn(List.of(paymentEntry, own));

        assertThat(service.getAuditLogsFor(loner)).containsExactly(own);
    }
}
