package com.toir.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Comparator;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class FlywayEmptyDbSmokeTest {

    private static final Pattern MIGRATION_FILE = Pattern.compile("^[BV](.+?)__.*\\.sql$");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void migrateFromEmptyDatabaseShouldApplyBaselineSuccessfully() throws Exception {
        String expectedLatestMigration = latestMigrationScript();

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
            assertThat(rs.getString("script")).isEqualTo(expectedLatestMigration);
            assertThat(rs.getBoolean("success")).isTrue();
        }
    }

    private static String latestMigrationScript() throws Exception {
        try (var paths = Files.list(Path.of("src/main/resources/db/migration"))) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> MIGRATION_FILE.matcher(name).matches())
                    .max(Comparator.comparing(FlywayEmptyDbSmokeTest::versionOf))
                    .orElseThrow();
        }
    }

    private static MigrationVersion versionOf(String script) {
        var matcher = MIGRATION_FILE.matcher(script);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not a Flyway migration script: " + script);
        }
        return MigrationVersion.fromVersion(matcher.group(1).replace('_', '.'));
    }
}
