-- Flyway Migration V5: Internal RBAC/ABAC Authorization Model
--
-- ARCHITECTURAL RULE: Keycloak performs AuthN only. It signs JWTs and is never
-- consulted for an authorization decision. The permission matrix and every
-- contextual rule live in these tables and are evaluated in Spring Boot.

-- ---------------------------------------------------------------------------
-- 1. Internal identity becomes the source of truth for authorization
-- ---------------------------------------------------------------------------
ALTER TABLE users ADD COLUMN IF NOT EXISTS team VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS keycloak_subject VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS active BOOLEAN DEFAULT TRUE NOT NULL;

-- Maps the JWT "sub" claim to an internal user. NULL until first login.
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_keycloak_subject ON users(keycloak_subject);

-- V3 seeded DEVOPS_ENGINEER / SECURITY_LEAD, which are absent from the Role enum.
-- With @Enumerated(EnumType.STRING) any read of those rows throws. Reconcile them
-- onto the four canonical platform roles before the RBAC engine reads this table.
UPDATE users SET role = 'TECH_LEAD' WHERE role = 'DEVOPS_ENGINEER';
UPDATE users SET role = 'ADMIN'     WHERE role = 'SECURITY_LEAD';

-- Team assignment drives the ownerTeam == user.team ABAC rule.
UPDATE users SET team = 'Platform Infrastructure' WHERE username = 'admin'   AND team IS NULL;
UPDATE users SET team = 'Equipe Paiement'         WHERE username = 'alice'   AND team IS NULL;
UPDATE users SET team = 'Equipe Catalogue'        WHERE username = 'bob'     AND team IS NULL;
UPDATE users SET team = 'Platform Infrastructure' WHERE username = 'charlie' AND team IS NULL;
UPDATE users SET team = 'Security & IAM Team'     WHERE username = 'diana'   AND team IS NULL;

-- ---------------------------------------------------------------------------
-- 2. Resource criticality (tier-1 gating)
-- ---------------------------------------------------------------------------
ALTER TABLE services ADD COLUMN IF NOT EXISTS criticality VARCHAR(32) DEFAULT 'STANDARD';

UPDATE services SET criticality = 'TIER_1'
 WHERE id IN ('srv-payment', 'srv-auth', 'srv-k8s') AND criticality = 'STANDARD';

-- ---------------------------------------------------------------------------
-- 3. RBAC: coarse-grained role -> (resource_type, action) matrix, admin editable
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS role_permissions (
    id            VARCHAR(64)  PRIMARY KEY,
    role          VARCHAR(32)  NOT NULL,
    resource_type VARCHAR(64)  NOT NULL,
    action        VARCHAR(64)  NOT NULL,
    description   VARCHAR(500)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_role_permissions_unique
    ON role_permissions(role, resource_type, action);
CREATE INDEX IF NOT EXISTS idx_role_permissions_lookup
    ON role_permissions(role);

-- VIEWER: read-only across the catalog and metrics.
INSERT INTO role_permissions (id, role, resource_type, action, description) VALUES
('rp-001', 'VIEWER', 'SERVICE',       'READ',    'Browse the service catalog'),
('rp-002', 'VIEWER', 'OBSERVABILITY', 'READ',    'Read service health and metrics'),
('rp-003', 'VIEWER', 'FEATURE_FLAG',  'READ',    'View feature flag state'),
('rp-004', 'VIEWER', 'COPILOT',       'READ',    'View indexed documentation sources');

-- DEVELOPER: everything VIEWER has, plus scaffolding and own-team mutation.
INSERT INTO role_permissions (id, role, resource_type, action, description) VALUES
('rp-010', 'DEVELOPER', 'SERVICE',       'READ',    'Browse the service catalog'),
('rp-011', 'DEVELOPER', 'SERVICE',       'CREATE',  'Register a service owned by their team'),
('rp-012', 'DEVELOPER', 'SERVICE',       'UPDATE',  'Amend a service owned by their team'),
('rp-013', 'DEVELOPER', 'OBSERVABILITY', 'READ',    'Read service health and metrics'),
('rp-014', 'DEVELOPER', 'OBSERVABILITY', 'STREAM',  'Stream live deployment logs for their team'),
('rp-015', 'DEVELOPER', 'SCAFFOLD',      'CREATE',  'Scaffold a new project'),
('rp-016', 'DEVELOPER', 'SCAFFOLD',      'READ',    'Track scaffolding job progress'),
('rp-017', 'DEVELOPER', 'FEATURE_FLAG',  'READ',    'View feature flag state'),
('rp-018', 'DEVELOPER', 'FEATURE_FLAG',  'CREATE',  'Create a feature flag for their team'),
('rp-019', 'DEVELOPER', 'FEATURE_FLAG',  'ROLLOUT', 'Advance canary rollout (bounded by ABAC)'),
('rp-020', 'DEVELOPER', 'COPILOT',       'EXECUTE', 'Query the IDP Copilot'),
('rp-021', 'DEVELOPER', 'GITHUB',        'READ',    'List connected repositories'),
('rp-022', 'DEVELOPER', 'DEVOPS',        'READ',    'Read cluster and cost dashboards'),
('rp-023', 'DEVELOPER', 'COPILOT',       'READ',    'View indexed documentation sources');

-- TECH_LEAD: everything DEVELOPER has, plus deletion, audit and RAG ingestion.
INSERT INTO role_permissions (id, role, resource_type, action, description) VALUES
('rp-030', 'TECH_LEAD', 'SERVICE',       'READ',    'Browse the service catalog'),
('rp-031', 'TECH_LEAD', 'SERVICE',       'CREATE',  'Register a service'),
('rp-032', 'TECH_LEAD', 'SERVICE',       'UPDATE',  'Amend a service'),
('rp-033', 'TECH_LEAD', 'SERVICE',       'DELETE',  'Retire a service (bounded by ABAC)'),
('rp-034', 'TECH_LEAD', 'OBSERVABILITY', 'READ',    'Read service health and metrics'),
('rp-035', 'TECH_LEAD', 'OBSERVABILITY', 'STREAM',  'Stream live deployment logs'),
('rp-036', 'TECH_LEAD', 'SCAFFOLD',      'CREATE',  'Scaffold a new project'),
('rp-037', 'TECH_LEAD', 'SCAFFOLD',      'READ',    'Track scaffolding job progress'),
('rp-038', 'TECH_LEAD', 'FEATURE_FLAG',  'READ',    'View feature flag state'),
('rp-039', 'TECH_LEAD', 'FEATURE_FLAG',  'CREATE',  'Create a feature flag'),
('rp-040', 'TECH_LEAD', 'FEATURE_FLAG',  'UPDATE',  'Toggle a feature flag'),
('rp-041', 'TECH_LEAD', 'FEATURE_FLAG',  'ROLLOUT', 'Advance canary rollout to 100%'),
('rp-042', 'TECH_LEAD', 'FEATURE_FLAG',  'DELETE',  'Remove a feature flag (bounded by ABAC)'),
('rp-043', 'TECH_LEAD', 'AUDIT_LOG',     'READ',    'Read the audit trail for their team'),
('rp-044', 'TECH_LEAD', 'COPILOT',       'EXECUTE', 'Query the IDP Copilot'),
('rp-045', 'TECH_LEAD', 'COPILOT',       'INGEST',  'Trigger RAG ingestion for their team'),
('rp-046', 'TECH_LEAD', 'GITHUB',        'READ',    'List connected repositories'),
('rp-047', 'TECH_LEAD', 'GITHUB',        'CREATE',  'Import a repository into the catalog'),
('rp-048', 'TECH_LEAD', 'DEVOPS',        'READ',    'Read cluster and cost dashboards'),
('rp-049', 'TECH_LEAD', 'DEVOPS',        'EXECUTE', 'Trigger a CI/CD pipeline run'),
('rp-050', 'TECH_LEAD', 'COPILOT',       'READ',    'View indexed documentation sources');

-- ADMIN: full surface, including the RBAC/ABAC administration itself.
INSERT INTO role_permissions (id, role, resource_type, action, description) VALUES
('rp-060', 'ADMIN', 'SERVICE',       'READ',    'Browse the service catalog'),
('rp-061', 'ADMIN', 'SERVICE',       'CREATE',  'Register a service'),
('rp-062', 'ADMIN', 'SERVICE',       'UPDATE',  'Amend a service'),
('rp-063', 'ADMIN', 'SERVICE',       'DELETE',  'Retire any service'),
('rp-064', 'ADMIN', 'OBSERVABILITY', 'READ',    'Read service health and metrics'),
('rp-065', 'ADMIN', 'OBSERVABILITY', 'STREAM',  'Stream live deployment logs'),
('rp-066', 'ADMIN', 'SCAFFOLD',      'CREATE',  'Scaffold a new project'),
('rp-067', 'ADMIN', 'SCAFFOLD',      'READ',    'Track scaffolding job progress'),
('rp-068', 'ADMIN', 'FEATURE_FLAG',  'READ',    'View feature flag state'),
('rp-069', 'ADMIN', 'FEATURE_FLAG',  'CREATE',  'Create a feature flag'),
('rp-070', 'ADMIN', 'FEATURE_FLAG',  'UPDATE',  'Toggle a feature flag'),
('rp-071', 'ADMIN', 'FEATURE_FLAG',  'ROLLOUT', 'Advance canary rollout without bound'),
('rp-072', 'ADMIN', 'FEATURE_FLAG',  'DELETE',  'Remove any feature flag'),
('rp-073', 'ADMIN', 'AUDIT_LOG',     'READ',    'Read the full unfiltered audit trail'),
('rp-074', 'ADMIN', 'COPILOT',       'EXECUTE', 'Query the IDP Copilot'),
('rp-075', 'ADMIN', 'COPILOT',       'INGEST',  'Trigger RAG ingestion for any source'),
('rp-076', 'ADMIN', 'GITHUB',        'READ',    'List connected repositories'),
('rp-077', 'ADMIN', 'GITHUB',        'CREATE',  'Import a repository into the catalog'),
('rp-078', 'ADMIN', 'DEVOPS',        'READ',    'Read cluster and cost dashboards'),
('rp-079', 'ADMIN', 'DEVOPS',        'EXECUTE', 'Trigger a CI/CD pipeline run'),
('rp-080', 'ADMIN', 'ADMIN',         'READ',    'Read users, roles and policies'),
('rp-081', 'ADMIN', 'ADMIN',         'MANAGE',  'Edit the RBAC matrix and ABAC policies'),
('rp-082', 'ADMIN', 'COPILOT',       'READ',    'View indexed documentation sources');

-- ---------------------------------------------------------------------------
-- 4. ABAC: contextual rules layered on top of the RBAC grant.
--
--    Evaluation contract (see PolicyDecisionService):
--      * Only rows matching (resource_type, action) apply; '*' is a wildcard.
--      * A row's condition columns are AND-ed. NULL/FALSE means "not constrained".
--      * A matching DENY row always wins; rows are ordered by priority ascending.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS abac_policies (
    id                   VARCHAR(64)  PRIMARY KEY,
    name                 VARCHAR(255) NOT NULL,
    description          VARCHAR(500),
    resource_type        VARCHAR(64)  NOT NULL,
    action               VARCHAR(64)  NOT NULL,
    effect               VARCHAR(16)  NOT NULL,
    -- Condition attributes. NULL / FALSE = this policy does not constrain it.
    require_same_team    BOOLEAN      NOT NULL DEFAULT FALSE,
    min_role             VARCHAR(32),
    environment          VARCHAR(32),
    criticality          VARCHAR(32),
    max_rollout_percent  INT,
    require_corporate_ip BOOLEAN      NOT NULL DEFAULT FALSE,
    priority             INT          NOT NULL DEFAULT 100,
    enabled              BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_abac_policies_lookup
    ON abac_policies(resource_type, action, enabled);

INSERT INTO abac_policies
    (id, name, description, resource_type, action, effect,
     require_same_team, min_role, environment, criticality, max_rollout_percent, require_corporate_ip, priority, enabled)
VALUES
-- Ownership: a DEVELOPER may only mutate services belonging to their own team.
('pol-001', 'Own-team service mutation',
 'A service may only be created or amended by a member of its owning team; TECH_LEAD and above are exempt.',
 'SERVICE', 'UPDATE', 'DENY', TRUE, 'TECH_LEAD', NULL, NULL, NULL, FALSE, 10, TRUE),
('pol-002', 'Own-team service creation',
 'A service must be registered under the creator''s own team; TECH_LEAD and above are exempt.',
 'SERVICE', 'CREATE', 'DENY', TRUE, 'TECH_LEAD', NULL, NULL, NULL, FALSE, 10, TRUE),

-- Production hardening: prod changes require TECH_LEAD regardless of RBAC grant.
('pol-010', 'Production change requires TECH_LEAD',
 'Any mutation targeting a PROD service requires TECH_LEAD or ADMIN.',
 'SERVICE', 'UPDATE', 'DENY', FALSE, 'TECH_LEAD', 'PROD', NULL, NULL, FALSE, 20, TRUE),

-- Tier-1 criticality: deletion of a tier-1 service is ADMIN-only (double validation).
('pol-020', 'Tier-1 deletion is ADMIN only',
 'Retiring a tier-1 critical service requires ADMIN.',
 'SERVICE', 'DELETE', 'DENY', FALSE, 'ADMIN', NULL, 'TIER_1', NULL, FALSE, 20, TRUE),
('pol-021', 'Service deletion from corporate network',
 'DELETE on the catalog must originate from a corporate IP range.',
 'SERVICE', 'DELETE', 'DENY', FALSE, NULL, NULL, NULL, NULL, TRUE, 30, TRUE),

-- Canary rollout ceilings.
('pol-030', 'Rollout above 50% requires TECH_LEAD',
 'A DEVELOPER may advance a canary rollout only up to 50%.',
 'FEATURE_FLAG', 'ROLLOUT', 'DENY', FALSE, 'TECH_LEAD', NULL, NULL, 50, FALSE, 20, TRUE),
('pol-031', 'Tier-1 flag deletion is ADMIN only',
 'Removing a feature flag bound to a tier-1 service requires ADMIN.',
 'FEATURE_FLAG', 'DELETE', 'DENY', FALSE, 'ADMIN', NULL, 'TIER_1', NULL, FALSE, 20, TRUE),

-- Log streaming is restricted to the owning team below TECH_LEAD.
('pol-040', 'Own-team log streaming',
 'Live deployment logs are visible only to the owning team; TECH_LEAD and above are exempt.',
 'OBSERVABILITY', 'STREAM', 'DENY', TRUE, 'TECH_LEAD', NULL, NULL, NULL, FALSE, 10, TRUE),

-- RAG ingestion is scoped to the owning team below ADMIN.
('pol-050', 'Own-team RAG ingestion',
 'A TECH_LEAD may only trigger ingestion for sources owned by their team; ADMIN is exempt.',
 'COPILOT', 'INGEST', 'DENY', TRUE, 'ADMIN', NULL, NULL, NULL, FALSE, 10, TRUE);
