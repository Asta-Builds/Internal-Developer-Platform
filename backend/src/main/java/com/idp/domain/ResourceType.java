package com.idp.domain;

/**
 * Protected resource families. Every authorization check names one of these,
 * paired with a {@link PermissionAction}.
 */
public enum ResourceType {
    SERVICE,
    SCAFFOLD,
    FEATURE_FLAG,
    OBSERVABILITY,
    AUDIT_LOG,
    COPILOT,
    GITHUB,
    DEVOPS,
    ADMIN
}
