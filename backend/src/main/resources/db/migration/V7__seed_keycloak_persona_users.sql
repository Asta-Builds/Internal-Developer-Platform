-- Flyway Migration V7: Seed Keycloak persona users into the internal users table.
--
-- The realm in keycloak/idp-realm.json ships four personas: admin, tech_lead,
-- developer and viewer. 'admin' already has an internal row from V2 (usr-1), so only
-- the remaining three are seeded here.
--
-- Why this migration exists: authorization is owned by this table, never by Keycloak.
-- Without a row, a persona logging in is either refused (prod default) or
-- auto-provisioned as VIEWER (IDP_SECURITY_AUTO_PROVISION=true in docker-compose),
-- which makes the local realm's roles look broken. Seeding the rows makes the
-- platform-side role the authority, exactly as intended.
--
-- keycloak_subject is deliberately left NULL: InternalUserReconciliationService binds
-- it on first login via the findByUsername fallback, which keeps this migration
-- independent of whatever subject ids the realm import ends up assigning.
--
-- Upsert style: UPDATE-then-INSERT-WHERE-NOT-EXISTS rather than ON CONFLICT.
--   * It keys on username, not id. On a dev database where auto-provisioning already
--     created these principals, the existing rows carry generated ids (usr-<random>),
--     so an id-based upsert falls through to the username UNIQUE constraint from V1
--     and aborts the migration.
--   * ON CONFLICT ... DO UPDATE is PostgreSQL-only — H2 rejects it even in
--     PostgreSQL mode, and FlywayMigrationTest applies this chain to H2. The two
--     plain statements below run identically on both.
-- Updating by username also preserves any keycloak_subject already bound on the row.

UPDATE users SET email = 'lead@company.internal', role = 'TECH_LEAD',
                 team = 'Platform Infrastructure', active = TRUE
WHERE username = 'tech_lead';

INSERT INTO users (id, username, email, role, team, active)
SELECT 'usr-lead-02', 'tech_lead', 'lead@company.internal', 'TECH_LEAD', 'Platform Infrastructure', TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'tech_lead');

UPDATE users SET email = 'dev@company.internal', role = 'DEVELOPER',
                 team = 'Equipe Paiement', active = TRUE
WHERE username = 'developer';

INSERT INTO users (id, username, email, role, team, active)
SELECT 'usr-dev-03', 'developer', 'dev@company.internal', 'DEVELOPER', 'Equipe Paiement', TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'developer');

UPDATE users SET email = 'viewer@company.internal', role = 'VIEWER',
                 team = 'Security & IAM Team', active = TRUE
WHERE username = 'viewer';

INSERT INTO users (id, username, email, role, team, active)
SELECT 'usr-viewer-04', 'viewer', 'viewer@company.internal', 'VIEWER', 'Security & IAM Team', TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'viewer');
