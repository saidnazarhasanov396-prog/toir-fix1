package com.toir.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class FlywayEmptyDbSmokeTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void migrateFromEmptyDatabaseShouldApplyBaselineSuccessfully() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .validateOnMigrate(true)
                .load();

        MigrateResult result = flyway.migrate();

        assertThat(result.success).isTrue();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT version, script, success " +
                             "FROM flyway_schema_history " +
                             "ORDER BY installed_rank DESC LIMIT 1"
             );
             ResultSet rs = statement.executeQuery()) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("version")).isEqualTo("20260523.7");
            assertThat(rs.getString("script")).isEqualTo("B20260523_7__schema_baseline.sql");
            assertThat(rs.getBoolean("success")).isTrue();
        }
    }
}
