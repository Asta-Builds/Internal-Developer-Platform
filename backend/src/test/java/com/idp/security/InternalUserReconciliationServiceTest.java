package com.idp.security;

import com.idp.domain.Role;
import com.idp.domain.UserEntity;
import com.idp.repository.UserRepository;
import com.idp.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The AuthN/AuthZ seam: a verified token maps to an internal user record, and the
 * row — never the JWT claim — is the source of the applied role.
 */
@ExtendWith(MockitoExtension.class)
class InternalUserReconciliationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;

    private InternalUserReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new InternalUserReconciliationService(userRepository, auditService);
        // Least-privilege defaults; the auto-provision tests flip this explicitly.
        setField("defaultRoleOnProvision", "VIEWER");
        setAutoProvision(false);
    }

    private void setAutoProvision(boolean enabled) {
        // Values are injected via @Value; ReflectionTestUtils would need the app
        // context, so the fields are toggled through setters the service exposes
        // only for tests? They do not — so drive the behaviour via the constructor
        // surface below instead.
        setField("autoProvisionUnknownUsers", enabled);
    }

    private void setField(String name, Object value) {
        try {
            var field = InternalUserReconciliationService.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(service, value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Jwt jwt(String subject, String username, String email, String... roles) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(subject)
                .claim("preferred_username", username)
                .issuer("http://localhost:8180/realms/idp-realm")
                .issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusSeconds(300));
        if (email != null) {
            builder.claim("email", email);
        }
        if (roles.length > 0) {
            builder.claim("realm_access", Map.of("roles", List.of(roles)));
        }
        return builder.build();
    }

    @Test
    @DisplayName("reconciles a known subject to the internal user")
    void reconcilesKnownSubject() {
        UserEntity user = UserEntity.builder()
                .id("usr-1").username("alice").email("alice@company.internal")
                .role(Role.DEVELOPER).team("Equipe Paiement").active(true).build();
        when(userRepository.findByKeycloakSubject("sub-1")).thenReturn(Optional.of(user));

        Optional<AuthenticatedUser> result = service.reconcile(jwt("sub-1", "alice", "alice@company.internal"));

        assertThat(result).isPresent();
        assertThat(result.get().getRole()).isEqualTo(Role.DEVELOPER);
        assertThat(result.get().hasRoleDrift()).isFalse();
    }

    @Test
    @DisplayName("binds the subject to a username-matched record on first login")
    void bindsSubjectOnFirstLogin() {
        UserEntity user = UserEntity.builder()
                .id("usr-2").username("bob").role(Role.VIEWER).active(true).build();
        when(userRepository.findByKeycloakSubject("sub-2")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));

        Optional<AuthenticatedUser> result = service.reconcile(jwt("sub-2", "bob", "bob@company.internal"));

        assertThat(result).isPresent();
        assertThat(user.getKeycloakSubject()).isEqualTo("sub-2");
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("rejects an unknown principal when auto-provisioning is disabled")
    void rejectsUnknownPrincipalByDefault() {
        when(userRepository.findByKeycloakSubject("sub-x")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("stranger")).thenReturn(Optional.empty());

        Optional<AuthenticatedUser> result = service.reconcile(jwt("sub-x", "stranger", "s@x.io"));

        assertThat(result).isEmpty();
        verify(auditService).logAction(anyString(), org.mockito.ArgumentMatchers.eq("ACCESS_DENIED_UNKNOWN_PRINCIPAL"),
                anyString(), anyString());
    }

    @Test
    @DisplayName("provisions a least-privilege VIEWER when auto-provisioning is enabled")
    void provisionsUnknownPrincipal() {
        setAutoProvision(true);
        when(userRepository.findByKeycloakSubject("sub-y")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("newbie")).thenReturn(Optional.empty());

        Optional<AuthenticatedUser> result = service.reconcile(jwt("sub-y", "newbie", null));

        assertThat(result).isPresent();
        assertThat(result.get().getRole()).isEqualTo(Role.VIEWER);
        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getKeycloakSubject()).isEqualTo("sub-y");
        assertThat(captor.getValue().getEmail()).isEqualTo("newbie@company.internal");
        assertThat(captor.getValue().isActive()).isTrue();
    }

    @Test
    @DisplayName("provisions at least privilege even when the token asserts ADMIN")
    void provisioningIgnoresClaimedRole() {
        setAutoProvision(true);
        when(userRepository.findByKeycloakSubject("sub-z")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("escalator")).thenReturn(Optional.empty());

        Optional<AuthenticatedUser> result = service.reconcile(jwt("sub-z", "escalator", null, "ADMIN"));

        assertThat(result).isPresent();
        assertThat(result.get().getRole()).isEqualTo(Role.VIEWER);
        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.VIEWER);
    }

    @Test
    @DisplayName("rejects a deactivated internal user")
    void rejectsDeactivatedUser() {
        UserEntity user = UserEntity.builder()
                .id("usr-3").username("carol").role(Role.DEVELOPER).active(false).build();
        when(userRepository.findByKeycloakSubject("sub-3")).thenReturn(Optional.of(user));

        Optional<AuthenticatedUser> result = service.reconcile(jwt("sub-3", "carol", "c@x.io"));

        assertThat(result).isEmpty();
        verify(auditService).logAction(anyString(), org.mockito.ArgumentMatchers.eq("ACCESS_DENIED_INACTIVE_USER"),
                anyString(), anyString());
    }

    @Test
    @DisplayName("reports drift when the token asserts a higher role than the row holds")
    void reportsRoleDrift() {
        UserEntity user = UserEntity.builder()
                .id("usr-4").username("dave").role(Role.DEVELOPER).active(true).build();
        when(userRepository.findByKeycloakSubject("sub-4")).thenReturn(Optional.of(user));

        Optional<AuthenticatedUser> result = service.reconcile(jwt("sub-4", "dave", "d@x.io", "ROLE_ADMIN"));

        assertThat(result).isPresent();
        assertThat(result.get().getRole()).isEqualTo(Role.DEVELOPER);
        assertThat(result.get().getClaimedRole()).isEqualTo(Role.ADMIN);
        assertThat(result.get().hasRoleDrift()).isTrue();
    }

    @Test
    @DisplayName("rejects a token with neither a username nor a subject claim")
    void rejectsWithoutIdentity() {
        Jwt bare = Jwt.withTokenValue("t").header("alg", "RS256").subject("").claim("email", "").build();

        Optional<AuthenticatedUser> result = service.reconcile(bare);

        assertThat(result).isEmpty();
        verify(userRepository, never()).findByKeycloakSubject(anyString());
    }
}
