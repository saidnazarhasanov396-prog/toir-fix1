package com.toir.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepairCampaignWorkSourceMigrationPostgresTest {

    @Test
    void emptyPostgres17FlywayChainEnforcesV1AndImmutableV2Contracts() throws Exception {
        Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("TOIR_LOCAL_PG17")),
                "Set TOIR_LOCAL_PG17=true to run the local PostgreSQL 17 migration probe");
        String database = "task2_chain_" + UUID.randomUUID().toString().replace("-", "");
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
            var result = Flyway.configure()
                    .dataSource(databaseUrl, user(), password())
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .validateOnMigrate(true)
                    .load()
                    .migrate();
            assertThat(result.success).isTrue();

            try (Connection connection = connection(databaseUrl); Statement statement = connection.createStatement()) {
                assertApplied(statement, "V20260712_1__repair_campaign_core.sql");
                assertApplied(statement, "V20260712_2__repair_campaign_work_sources.sql");
                assertThat(columnExists(statement, "repair_campaigns", "version")).isTrue();

                UUID campaignId = existingCampaign(statement); UUID equipmentId = existingEquipment(statement);
                UUID sourceId = UUID.randomUUID(); UUID otherSourceId = UUID.randomUUID();
                UUID firstItem = insert(statement, campaignId, equipmentId, sourceId, 0, "PENDING");
                assertConstraint(() -> insert(statement, campaignId, equipmentId, sourceId, 1, "PENDING"),
                        "uq_repair_campaign_work_items_active_source");
                assertConstraint(() -> insert(statement, campaignId, equipmentId, otherSourceId, 0, "PENDING"),
                        "uq_repair_campaign_work_items_active_order");
                statement.execute("UPDATE repair_campaign_work_items SET is_deleted=true WHERE id='" + firstItem + "'");
                insert(statement, campaignId, equipmentId, sourceId, 0, "REPLAN_REQUIRED");
                assertConstraint(() -> insert(statement, campaignId, equipmentId, UUID.randomUUID(), 2,
                        "CLIENT_FORGED"), "chk_repair_campaign_work_items_status");
                try (ResultSet version = statement.executeQuery(
                        "UPDATE repair_campaigns SET version=version+1 WHERE id='" + campaignId
                                + "' RETURNING version")) {
                    assertThat(version.next()).isTrue();
                    assertThat(version.getLong(1)).isGreaterThan(0L);
                }
            }
        } finally {
            try (Connection admin = connection(adminUrl); Statement statement = admin.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
            }
        }
    }

    private static void assertApplied(Statement statement, String script) throws SQLException {
        try (ResultSet result = statement.executeQuery("SELECT success FROM flyway_schema_history WHERE script='"
                + script + "'")) {
            assertThat(result.next()).as(script).isTrue();
            assertThat(result.getBoolean(1)).isTrue();
        }
    }

    private static boolean columnExists(Statement statement, String table, String column) throws SQLException {
        try (ResultSet result = statement.executeQuery("SELECT EXISTS (SELECT 1 FROM information_schema.columns "
                + "WHERE table_schema='public' AND table_name='" + table + "' AND column_name='" + column + "')")) {
            result.next(); return result.getBoolean(1);
        }
    }

    private static UUID existingCampaign(Statement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery("SELECT id FROM repair_campaigns LIMIT 1")) {
            if (result.next()) return result.getObject(1, UUID.class);
        }
        UUID id = UUID.randomUUID();
        statement.execute("INSERT INTO repair_campaigns(id,code,name,status,start_date,end_date,total_budget,total_actual,"
                + "currency_code,scope_type,version,closure_version,is_deleted,created_at,updated_at) VALUES ('" + id
                + "','RC-PG','PG','DRAFT',current_date,current_date,0,0,'UZS','CUSTOM',0,0,false,now(),now())");
        return id;
    }

    private static UUID existingEquipment(Statement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery("SELECT id FROM equipment LIMIT 1")) {
            if (result.next()) return result.getObject(1, UUID.class);
        }
        UUID typeId = UUID.randomUUID(); UUID id = UUID.randomUUID();
        statement.execute("INSERT INTO equipment_types(id,code,name,category,is_deleted,created_at,updated_at) VALUES ('"
                + typeId + "','TYPE-PG','PG type','PRODUCTION_EQUIPMENT',false,now(),now())");
        statement.execute("INSERT INTO equipment(id,equipment_type_id,code,name,inventory_number,category,status,"
                + "is_deleted,created_at,updated_at) VALUES ('" + id + "','" + typeId
                + "','EQ-PG','PG equipment','INV-PG','PRODUCTION_EQUIPMENT','ACTIVE',false,now(),now())");
        return id;
    }

    private static UUID insert(Statement statement, UUID campaignId, UUID equipmentId,
                               UUID sourceId, int order, String status) throws SQLException {
        UUID id = UUID.randomUUID();
        statement.execute("INSERT INTO repair_campaign_work_items(id,repair_campaign_id,source_type,source_id,"
                + "equipment_id,title,status,order_number,created_at,updated_at) VALUES ('" + id + "','"
                + campaignId + "','DEFECT','" + sourceId + "','" + equipmentId + "','source','"
                + status + "'," + order + ",now(),now())");
        return id;
    }

    private static void assertConstraint(SqlCall call, String constraint) {
        assertThatThrownBy(call::run).isInstanceOf(SQLException.class).hasMessageContaining(constraint);
    }

    private static Connection connection(String url) throws SQLException {
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

    @FunctionalInterface
    private interface SqlCall { void run() throws SQLException; }
}
