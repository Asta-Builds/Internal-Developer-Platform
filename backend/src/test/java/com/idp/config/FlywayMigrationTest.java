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
    @DisplayName("applies every migration cleanly, including V5")
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
                .contains("1", "2", "3", "4", "5");
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
