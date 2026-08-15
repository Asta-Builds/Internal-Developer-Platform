package com.idp.scaffold;

import com.idp.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Guards the switch that decides whether the platform reaches out to GitHub at all.
 *
 * <p>The enabled path is not exercised here — it creates real repositories, and a
 * test that could do so by misconfiguration is worse than no test. The git half is
 * covered against a local remote in {@link GitRepositoryPusherTest}.
 */
@ExtendWith(MockitoExtension.class)
class GitHubRepositoryPublisherTest {

    @Mock private GitRepositoryPusher pusher;
    @Mock private AuditService auditService;

    private GitHubRepositoryPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new GitHubRepositoryPublisher(pusher, auditService);
        set(false, "", "", true);
    }

    private void set(boolean enabled, String token, String org, boolean isPrivate) {
        ReflectionTestUtils.setField(publisher, "enabled", enabled);
        ReflectionTestUtils.setField(publisher, "token", token);
        ReflectionTestUtils.setField(publisher, "organisation", org);
        ReflectionTestUtils.setField(publisher, "apiUrl", "https://api.github.com");
        ReflectionTestUtils.setField(publisher, "privateRepository", isPrivate);
        ReflectionTestUtils.setField(publisher, "defaultBranch", "main");
    }

    @Test
    @DisplayName("is disabled by default so no scaffold can create a repository by accident")
    void disabledByDefault() {
        assertThat(publisher.isEnabled()).isFalse();
        assertThat(publisher.disabledReason()).contains("idp.scaffold.github.enabled=false");
    }

    @Test
    @DisplayName("stays disabled when switched on without a token")
    void requiresTokenWhenEnabled() {
        // Enabling without a credential is a misconfiguration, not a request to try
        // anonymously — GitHub would reject it and the pipeline would fail late.
        set(true, "  ", "", true);

        assertThat(publisher.isEnabled()).isFalse();
        assertThat(publisher.disabledReason()).contains("no token is configured");
    }

    @Test
    @DisplayName("publishes nothing and touches no collaborator while disabled")
    void publishIsNoOpWhileDisabled(@TempDir Path projectDir) {
        assertThat(publisher.publish(projectDir, "demo-service", "A demo", "scaffolder")).isEmpty();

        // Nothing reached the network or the audit trail — the scaffold simply
        // records that the project was not published.
        verifyNoInteractions(pusher, auditService);
    }

    @Test
    @DisplayName("becomes enabled once switched on with a token")
    void enabledWithTokenAndFlag() {
        set(true, "ghp_example", "platform-org", true);

        assertThat(publisher.isEnabled()).isTrue();
    }
}
