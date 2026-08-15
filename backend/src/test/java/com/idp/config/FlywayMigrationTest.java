package com.idp.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Applies the full migration chain to a throwaway H2 database in PostgreSQL mode.
 *
 * <p>Guards two things that would otherwise only surface at startup: that V5's DDL is
 * valid, and that no seeded user is left carrying a role outside the {@link
 * com.idp.domain.Role} enum — which would make {@code @Enumerated(EnumType.STRING)}
 * throw on read.
 */
class FlywayMigrationTest {

    private DataSource h2DataSource(String name) {
        org.h2.jdbcx.JdbcDataSource dataSource = new org.h2.jdbcx.JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private Flyway migrate(String name) {
        Flyway flyway = Flyway.configure()
                .dataSource(h2DataSource(name))
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();
        flyway.migrate();
        return flyway;
    }

    @Test
    @DisplayName("applies every migration cleanly, including V6")
    void appliesAllMigrations() {
        MigrateResult result = Flyway.configure()
                .dataSource(h2DataSource("migrate_all"))
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrations).extracting(m -> m.version)
                .contains("1", "2", "3", "4", "5", "6", "7", "8");
    }

    @Test
    @DisplayName("seeds the Keycloak personas with their platform roles and teams")
    void seedsKeycloakPersonas() throws Exception {
        Flyway flyway = migrate("keycloak_personas");

        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection();
             Statement statement = connection.createStatement()) {

            // Every realm persona must resolve to an internal row, or logging in as
            // that persona is refused (or silently downgraded to VIEWER).
            for (String username : new String[] { "admin", "tech_lead", "developer", "viewer" }) {
                assertThat(count(statement,
                        "SELECT COUNT(*) FROM users WHERE username = '" + username + "' AND active = TRUE"))
                        .as("internal row for Keycloak persona %s", username)
                        .isEqualTo(1);
            }

            assertThat(count(statement,
                    "SELECT COUNT(*) FROM users WHERE username = 'tech_lead' AND role = 'TECH_LEAD'")).isEqualTo(1);
            assertThat(count(statement,
                    "SELECT COUNT(*) FROM users WHERE username = 'developer' AND role = 'DEVELOPER'")).isEqualTo(1);
            assertThat(count(statement,
                    "SELECT COUNT(*) FROM users WHERE username = 'viewer' AND role = 'VIEWER'")).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("V8 creates the chunk store the Copilot retrieves from")
    void createsRagChunkStore() throws Exception {
        Flyway flyway = migrate("rag_chunks");

        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection();
             Statement statement = connection.createStatement()) {

            // Empty until ingestion runs, but the table and its FK must exist —
            // and must be portable enough to apply on H2, which has no pgvector.
            assertThat(count(statement, "SELECT COUNT(*) FROM rag_chunks")).isZero();

            statement.execute("INSERT INTO rag_chunks "
                    + "(id, document_id, chunk_index, content, embedding, embedding_dimension, source_hash, indexed_at) "
                    + "VALUES ('c1', 'doc-1', 0, 'passage', '0.1,0.2', 2, 'hash', CURRENT_TIMESTAMP)");
            assertThat(count(statement, "SELECT COUNT(*) FROM rag_chunks")).isEqualTo(1);

            // Chunks die with their document rather than dangling.
            statement.execute("DELETE FROM rag_documents WHERE id = 'doc-1'");
            assertThat(count(statement, "SELECT COUNT(*) FROM rag_chunks")).isZero();
        }
    }

    @Test
    @DisplayName("every service carries ownership, contact and documentation metadata")
    void servicesCarryOwnershipMetadata() throws Exception {
        Flyway flyway = migrate("catalog_ownership");

        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection();
             Statement statement = connection.createStatement()) {

            assertThat(count(statement, "SELECT COUNT(*) FROM services")).isEqualTo(10);

            // No service may be registered without an owning team, a contact
            // channel, a technical documentation link, Grafana monitoring link, and SLO/Scorecard metadata.
            assertThat(count(statement, "SELECT COUNT(*) FROM services WHERE owner_team IS NULL")).isZero();
            assertThat(count(statement, "SELECT COUNT(*) FROM services WHERE contact_channel IS NULL")).isZero();
            assertThat(count(statement, "SELECT COUNT(*) FROM services WHERE docs_url IS NULL")).isZero();
            assertThat(count(statement, "SELECT COUNT(*) FROM services WHERE grafana_url IS NULL")).isZero();
            assertThat(count(statement, "SELECT COUNT(*) FROM services WHERE slo_availability IS NULL")).isZero();
            assertThat(count(statement, "SELECT COUNT(*) FROM services WHERE escalation_policy IS NULL")).isZero();
            assertThat(count(statement, "SELECT COUNT(*) FROM services WHERE scorecard_grade IS NULL")).isZero();
        }
    }

    @Test
    @DisplayName("dependency map covers services, external APIs, databases and queues")
    void dependencyMapCoversAllResourceKinds() throws Exception {
        Flyway flyway = migrate("catalog_dependencies");

        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection();
             Statement statement = connection.createStatement()) {

            // External integrations (Stripe, Twilio, SendGrid, GitHub) are now
            // first-class dependency rows instead of free-text descriptions.
            assertThat(count(statement,
                    "SELECT COUNT(*) FROM dependencies WHERE target_external IS NOT NULL"))
                    .isPositive();

            for (String type : new String[] { "REST", "DB", "KAFKA" }) {
                assertThat(count(statement,
                        "SELECT COUNT(*) FROM dependencies WHERE type = '" + type + "'"))
                        .as("dependencies of type %s", type)
                        .isPositive();
            }

            // Every dependency declares a direction with the documented default.
            assertThat(count(statement,
                    "SELECT COUNT(*) FROM dependencies WHERE direction NOT IN ('DOWNSTREAM','UPSTREAM')"))
                    .isZero();

            // No dangling pointer: a dependency is either a registered service
            // or an explicitly named external resource, never both.
            assertThat(count(statement,
                    "SELECT COUNT(*) FROM dependencies WHERE (target_service_id IS NULL) = (target_external IS NULL)"))
                    .isZero();
        }
    }

    @Test
    @DisplayName("pgvector knowledge base stores documentation and vector embeddings")
    void pgVectorKnowledgeBaseStoresDocs() throws Exception {
        Flyway flyway = migrate("catalog_rag");

        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection();
             Statement statement = connection.createStatement()) {

            assertThat(count(statement, "SELECT COUNT(*) FROM rag_documents")).isGreaterThanOrEqualTo(5);
            assertThat(count(statement, "SELECT COUNT(*) FROM rag_documents WHERE content IS NULL")).isZero();
            assertThat(count(statement, "SELECT COUNT(*) FROM rag_documents WHERE doc_type IS NULL")).isZero();
        }
    }

    @Test
    @DisplayName("every service exposes at least one documented API contract")
    void everyServiceHasApiContracts() throws Exception {
        Flyway flyway = migrate("catalog_contracts");

        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection();
             Statement statement = connection.createStatement()) {

            assertThat(count(statement,
                    "SELECT COUNT(*) FROM services s LEFT JOIN api_endpoints e ON e.service_id = s.id "
                            + "WHERE e.id IS NULL"))
                    .isZero();

            assertThat(count(statement,
                    "SELECT COUNT(*) FROM api_endpoints WHERE service_id = 'srv-backstage'"))
                    .isPositive();
        }
    }

    @Test
    @DisplayName("leaves no user holding a role outside the Role enum")
    void reconcilesLegacyRoles() throws Exception {
        Flyway flyway = migrate("role_drift");

        List<String> roles = new ArrayList<>();
        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT DISTINCT role FROM users")) {
            while (rs.next()) {
                roles.add(rs.getString(1));
            }
        }

        assertThat(roles).isNotEmpty();
        assertThat(roles).allSatisfy(role ->
                assertThat(role).isIn("ADMIN", "TECH_LEAD", "DEVELOPER", "VIEWER"));
        assertThat(roles).doesNotContain("DEVOPS_ENGINEER", "SECURITY_LEAD");
    }

    @Test
    @DisplayName("seeds an RBAC matrix and an ABAC policy set")
    void seedsAuthorizationModel() throws Exception {
        Flyway flyway = migrate("authz_seed");

        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection();
             Statement statement = connection.createStatement()) {

            assertThat(count(statement, "SELECT COUNT(*) FROM role_permissions")).isPositive();
            assertThat(count(statement, "SELECT COUNT(*) FROM abac_policies WHERE enabled = TRUE")).isPositive();

            // A VIEWER must never hold a mutating grant.
            assertThat(count(statement,
                    "SELECT COUNT(*) FROM role_permissions WHERE role = 'VIEWER' "
                            + "AND action IN ('CREATE','UPDATE','DELETE','MANAGE','EXECUTE','ROLLOUT')"))
                    .isZero();

            // Only ADMIN may administer the authorization model itself.
            assertThat(count(statement,
                    "SELECT COUNT(*) FROM role_permissions WHERE resource_type = 'ADMIN' AND role <> 'ADMIN'"))
                    .isZero();

            // Every user must be assigned a team for the ownership rules to work.
            assertThat(count(statement, "SELECT COUNT(*) FROM users WHERE team IS NULL")).isZero();
        }
    }

    private long count(Statement statement, String sql) throws Exception {
        try (ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
