package com.idp.domain;

/**
 * Verbs a permission can grant over a {@link ResourceType}.
 */
public enum PermissionAction {
    READ,
    CREATE,
    UPDATE,
    DELETE,
    /** Advance a feature flag canary rollout. Bounded by ABAC ceilings. */
    ROLLOUT,
    /** Open a long-lived SSE/WebSocket stream. */
    STREAM,
    /** Trigger RAG (re-)ingestion. */
    INGEST,
    /** Run an operational action, e.g. a CI/CD pipeline. */
    EXECUTE,
    /** Mutate the RBAC matrix or ABAC policy set. */
    MANAGE
}
