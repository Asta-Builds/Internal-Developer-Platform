package com.idp.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HMAC-SHA256 signatures make audit records tamper-evident.
 */
class CryptographicAuditSignerTest {

    private final CryptographicAuditSigner signer = new CryptographicAuditSigner();

    @Test
    @DisplayName("generates a 64-hex-char signature")
    void hmacGeneration() {
        String sig = signer.generateHmacSignature("usr-1", "SECURITY_AUDIT_ACCESS_GRANTED",
                "srv-petclinic", "2026-08-06 10:00:00");

        assertThat(sig).isNotNull().hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("is deterministic for identical inputs")
    void deterministic() {
        String first = signer.generateHmacSignature("usr-1", "A", "t-1", "ts");
        String second = signer.generateHmacSignature("usr-1", "A", "t-1", "ts");

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("differs when any input changes")
    void sensitiveToInputs() {
        String base = signer.generateHmacSignature("usr-1", "A", "t-1", "ts");

        assertThat(signer.generateHmacSignature("usr-2", "A", "t-1", "ts")).isNotEqualTo(base);
        assertThat(signer.generateHmacSignature("usr-1", "B", "t-1", "ts")).isNotEqualTo(base);
        assertThat(signer.generateHmacSignature("usr-1", "A", "t-2", "ts")).isNotEqualTo(base);
        assertThat(signer.generateHmacSignature("usr-1", "A", "t-1", "ts2")).isNotEqualTo(base);
    }

    @Test
    @DisplayName("tolerates null inputs without throwing")
    void toleratesNulls() {
        assertThat(signer.generateHmacSignature(null, null, null, null)).hasSize(64);
    }
}
