package com.idp.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Decides whether a request originated inside a corporate IP range, backing the
 * {@code require_corporate_ip} ABAC condition.
 *
 * <p>Ranges come from {@code idp.security.corporate-ip-ranges} as CIDR blocks. The
 * default set covers loopback and the RFC 1918 private space so that local and
 * in-cluster development is not locked out; tighten it per environment.
 */
@Component
@Slf4j
public class CorporateNetworkService {

    private final List<String> corporateRanges;

    public CorporateNetworkService(
            @Value("${idp.security.corporate-ip-ranges:127.0.0.0/8,::1/128,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16}")
            List<String> corporateRanges) {
        this.corporateRanges = corporateRanges;
    }

    /**
     * Fails closed: an unknown or unparseable client address is treated as external.
     */
    public boolean isCorporateAddress(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return false;
        }

        // Resolve the client address once, and only as a literal. The value can come
        // from an X-Forwarded-For header, so it must never reach a DNS resolver.
        InetAddress target = parseLiteral(clientIp);
        if (target == null) {
            log.warn("[ABAC] Treating unparseable client address '{}' as external", clientIp);
            return false;
        }

        for (String cidr : corporateRanges) {
            try {
                if (matches(target, clientIp, cidr.trim())) {
                    return true;
                }
            } catch (IllegalArgumentException ex) {
                log.warn("[ABAC] Skipping malformed corporate IP range '{}': {}", cidr, ex.getMessage());
            }
        }
        return false;
    }

    /**
     * Parses a numeric IPv4/IPv6 literal, returning null for anything else. Guards
     * against a hostname in the request triggering a lookup during authorization.
     */
    private InetAddress parseLiteral(String value) {
        boolean looksNumeric = value.indexOf(':') >= 0
                || value.chars().allMatch(c -> (c >= '0' && c <= '9') || c == '.');
        if (!looksNumeric) {
            return null;
        }
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException ex) {
            return null;
        }
    }

    private boolean matches(InetAddress target, String clientIp, String cidr) {
        int slash = cidr.indexOf('/');
        if (slash < 0) {
            return cidr.equals(clientIp);
        }

        InetAddress network = parseLiteral(cidr.substring(0, slash));
        if (network == null) {
            throw new IllegalArgumentException("Network portion is not an IP literal");
        }
        int prefixLength;
        try {
            prefixLength = Integer.parseInt(cidr.substring(slash + 1));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Prefix length is not a number");
        }

        byte[] targetBytes = target.getAddress();
        byte[] networkBytes = network.getAddress();

        // An IPv4 address never sits inside an IPv6 block, or vice versa.
        if (targetBytes.length != networkBytes.length) {
            return false;
        }
        if (prefixLength < 0 || prefixLength > targetBytes.length * 8) {
            throw new IllegalArgumentException("Prefix length out of range: " + prefixLength);
        }

        int fullBytes = prefixLength / 8;
        for (int i = 0; i < fullBytes; i++) {
            if (targetBytes[i] != networkBytes[i]) {
                return false;
            }
        }

        int remainingBits = prefixLength % 8;
        if (remainingBits == 0) {
            return true;
        }
        int mask = 0xFF << (8 - remainingBits);
        return (targetBytes[fullBytes] & mask) == (networkBytes[fullBytes] & mask);
    }
}
