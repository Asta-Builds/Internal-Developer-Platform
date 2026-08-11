package com.idp.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Entity defaults that must never regress: least-privilege-safe field values.
 */
class EntityDefaultsTest {

    @Test
    @DisplayName("users are active by default")
    void userActiveByDefault() {
        assertThat(UserEntity.builder().build().isActive()).isTrue();
    }

    @Test
    @DisplayName("services default to a healthy PROD STANDARD posture")
    void serviceDefaults() {
        ServiceEntity service = ServiceEntity.builder().id("srv-1").name("x").build();

        assertThat(service.getHealthPercent()).isEqualTo(99.9);
        assertThat(service.getLatencyMs()).isEqualTo(14);
        assertThat(service.getEnvironment()).isEqualTo("PROD");
        assertThat(service.getCriticality()).isEqualTo("STANDARD");
        assertThat(service.getExposedApis()).isEmpty();
        assertThat(service.getDependencies()).isEmpty();
    }

    @Test
    @DisplayName("ABAC policies are enabled with a default priority")
    void policyDefaults() {
        AbacPolicyEntity policy = AbacPolicyEntity.builder()
                .name("p").resourceType("SERVICE").action("READ")
                .effect(PolicyEffect.ALLOW).build();

        assertThat(policy.isEnabled()).isTrue();
        assertThat(policy.getPriority()).isEqualTo(100);
        assertThat(policy.isRequireSameTeam()).isFalse();
        assertThat(policy.isRequireCorporateIp()).isFalse();
    }

    @Test
    @DisplayName("audit entries stamp their timestamp on persist")
    void auditTimestampStamped() {
        AuditLogEntryEntity entry = AuditLogEntryEntity.builder().actorId("usr-1").build();
        entry.onCreate();

        assertThat(entry.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("feature flags stamp their update time")
    void flagTimestampStamped() {
        FeatureFlagEntity flag = FeatureFlagEntity.builder().id("ff-1").key("K").build();
        flag.onSave();

        assertThat(flag.getUpdatedAt()).isNotNull();
    }
}
