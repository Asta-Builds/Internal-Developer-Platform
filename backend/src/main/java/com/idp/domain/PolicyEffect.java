package com.idp.domain;

/**
 * Outcome an ABAC policy contributes when all of its conditions match.
 * A matching DENY always overrides any ALLOW.
 */
public enum PolicyEffect {
    ALLOW,
    DENY
}
