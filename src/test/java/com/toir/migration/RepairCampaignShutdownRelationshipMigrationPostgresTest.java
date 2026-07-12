package com.toir.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RepairCampaignShutdownRelationshipMigrationPostgresTest {

    @Test
    void emptyPostgres17FlywayChainInstallsV3OwnershipAndActiveUniquenessContracts() throws Exception {
        Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("TOIR_LOCAL_PG17")),
                "Set TOIR_LOCAL_PG17=true to run the local PostgreSQL 17 migration probe");
        String database = "task3_chain_" + UUID.randomUUID().toString().replace("-", "");
        String adminUrl = env("TOIR_LOCAL_PG_URL", "jdbc:postgresql://localhost:5432/postgres");
        String databaseUrl = databaseUrl(adminUrl, database);
        try (Connection admin = connection(adminUrl); Statement statement = admin.createStatement()) {
            try (ResultSet version = statement.executeQuery("SHOW server_version_num")) {
                assertThat(version.next()).isTrue();
                assertThat(version.getInt(1)).isBetween(170000, 179999);
            }
            statement.execute("CREATE DATABASE " + database);
        }
        try {
            var result = Flyway.configure().dataSource(databaseUrl, user(), password())
                    .locations("classpath:db/migration").baselineOnMigrate(true).baselineVersion("0")
                    .validateOnMigrate(true).load().migrate();
            assertThat(result.success).isTrue();
            try (Connection connection = connection(databaseUrl); Statement statement = connection.createStatement()) {
                assertThat(scalar(statement, "SELECT success FROM flyway_schema_history WHERE script="
                        + "'V20260712_3__repair_campaign_shutdown_relationship.sql'" )).isEqualTo("t");
                assertThat(scalar(statement, "SELECT indexdef FROM pg_indexes WHERE indexname="
                        + "'uq_planned_shutdown_campaigns_active_pair'"))
                        .contains("UNIQUE", "planned_shutdown_id", "repair_campaign_id", "WHERE (is_deleted = false)");
                assertThat(scalar(statement, "SELECT indexdef FROM pg_indexes WHERE indexname="
                        + "'uq_repair_campaign_work_item_windows_active_identity'"))
                        .contains("UNIQUE", "repair_campaign_work_item_id", "planned_shutdown_id",
                                "shutdown_work_item_id", "WHERE (is_deleted = false)");
                assertThat(scalar(statement, "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname="
                        + "'fk_repair_campaign_work_item_windows_campaign_item'"))
                        .contains("FOREIGN KEY (repair_campaign_work_item_id, repair_campaign_id)");
                assertThat(scalar(statement, "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname="
                        + "'fk_repair_campaign_work_item_windows_shutdown_item_owner'"))
                        .contains("FOREIGN KEY (shutdown_work_item_id, planned_shutdown_id)");
            }
        } finally {
            try (Connection admin = connection(adminUrl); Statement statement = admin.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
            }
        }
    }

    private static String scalar(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).as(sql).isTrue();
            return result.getString(1);
        }
    }
    private static Connection connection(String url) throws Exception {
        return DriverManager.getConnection(url, user(), password());
    }
    private static String databaseUrl(String url, String database) {
        int query = url.indexOf('?'); String suffix = query < 0 ? "" : url.substring(query);
        String base = query < 0 ? url : url.substring(0, query);
        return base.substring(0, base.lastIndexOf('/') + 1) + database + suffix;
    }
    private static String user() { return env("TOIR_LOCAL_PG_USER", "postgres"); }
    private static String password() { return env("TOIR_LOCAL_PG_PASSWORD", ""); }
    private static String env(String name, String fallback) {
        String value = System.getenv(name); return value == null ? fallback : value;
    }
}
