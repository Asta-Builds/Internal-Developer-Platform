package com.idp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * In-memory idempotency cache backing safe retries of REST mutations.
 */
class IdempotencyServiceTest {

    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService();
    }

    @Test
    @DisplayName("stores and returns a cached result")
    void storesAndRetrieves() {
        service.storeResult("key-1", "payload-1");

        assertThat(service.getCachedResult("key-1")).contains("payload-1");
    }

    @Test
    @DisplayName("is empty for unknown keys")
    void emptyForUnknownKey() {
        assertThat(service.getCachedResult("key-nope")).isEmpty();
    }

    @Test
    @DisplayName("ignores blank keys and null results")
    void ignoresInvalidInput() {
        service.storeResult("  ", "x");
        service.storeResult(null, "x");
        service.storeResult("key-2", null);

        assertThat(service.getCachedResult("  ")).isEmpty();
        assertThat(service.getCachedResult(null)).isEmpty();
        assertThat(service.getCachedResult("key-2")).isEmpty();
    }
}
