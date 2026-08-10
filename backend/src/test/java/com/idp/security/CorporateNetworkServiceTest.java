package com.idp.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CIDR matching behind the {@code require_corporate_ip} ABAC condition.
 */
class CorporateNetworkServiceTest {

    private final CorporateNetworkService service =
            new CorporateNetworkService(List.of("10.0.0.0/8", "192.168.1.0/24", "127.0.0.1"));

    @ParameterizedTest
    @ValueSource(strings = {"10.0.0.1", "10.255.255.254", "192.168.1.7", "127.0.0.1"})
    @DisplayName("recognises addresses inside a configured range")
    void insideRange(String ip) {
        assertThat(service.isCorporateAddress(ip)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"11.0.0.1", "192.168.2.1", "203.0.113.7", "8.8.8.8"})
    @DisplayName("rejects addresses outside every configured range")
    void outsideRange(String ip) {
        assertThat(service.isCorporateAddress(ip)).isFalse();
    }

    @Test
    @DisplayName("fails closed on a null or blank address")
    void failsClosedOnMissingAddress() {
        assertThat(service.isCorporateAddress(null)).isFalse();
        assertThat(service.isCorporateAddress("")).isFalse();
    }

    @Test
    @DisplayName("fails closed rather than throwing on an unparseable address")
    void failsClosedOnGarbage() {
        assertThat(service.isCorporateAddress("not-an-ip")).isFalse();
    }

    @Test
    @DisplayName("does not match an IPv6 address against an IPv4 block")
    void doesNotCrossFamilies() {
        assertThat(service.isCorporateAddress("::1")).isFalse();
    }

    @Test
    @DisplayName("skips a malformed range instead of failing the whole check")
    void toleratesMalformedRange() {
        CorporateNetworkService withBadRange =
                new CorporateNetworkService(List.of("not-a-cidr/99", "10.0.0.0/8"));
        assertThat(withBadRange.isCorporateAddress("10.1.2.3")).isTrue();
    }
}
