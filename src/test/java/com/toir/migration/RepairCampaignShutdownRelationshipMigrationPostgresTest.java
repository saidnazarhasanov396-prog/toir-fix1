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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

                UUID departmentId = insertDepartment(statement);
                UUID equipmentId = insertEquipment(statement);
                UUID campaignId = insertCampaign(statement, departmentId, "RC-PG-1");
                UUID otherCampaignId = insertCampaign(statement, departmentId, "RC-PG-2");
                UUID shutdownId = insertShutdown(statement, departmentId, "PS-PG-1");
                UUID otherShutdownId = insertShutdown(statement, departmentId, "PS-PG-2");
                UUID campaignItemId = insertCampaignItem(statement, campaignId, equipmentId, 0);
                UUID otherCampaignItemId = insertCampaignItem(statement, campaignId, equipmentId, 1);
                UUID shutdownItemId = insertShutdownItem(statement, shutdownId, equipmentId, 0);
                UUID otherShutdownItemId = insertShutdownItem(statement, shutdownId, equipmentId, 1);

                UUID linkId = UUID.randomUUID();
                insertLink(statement, linkId, campaignId, shutdownId);
                assertConstraint(() -> insertLink(statement, UUID.randomUUID(), campaignId, shutdownId),
                        "uq_planned_shutdown_campaigns_active_pair");
                statement.execute("UPDATE planned_shutdown_campaigns SET is_deleted=true WHERE id='" + linkId + "'");
                insertLink(statement, UUID.randomUUID(), campaignId, shutdownId);

                UUID windowId = UUID.randomUUID();
                insertWindow(statement, windowId, campaignId, campaignItemId, shutdownId, shutdownItemId);
                assertConstraint(() -> insertWindow(statement, UUID.randomUUID(), campaignId, campaignItemId,
                        shutdownId, shutdownItemId), "uq_repair_campaign_work_item_windows_active_identity");
                statement.execute("UPDATE repair_campaign_work_item_windows SET is_deleted=true WHERE id='"
                        + windowId + "'");
                insertWindow(statement, UUID.randomUUID(), campaignId, campaignItemId, shutdownId, shutdownItemId);

                assertConstraint(() -> insertWindow(statement, UUID.randomUUID(), otherCampaignId, campaignItemId,
                        shutdownId, otherShutdownItemId), "fk_repair_campaign_work_item_windows_campaign_item");
                assertConstraint(() -> insertWindow(statement, UUID.randomUUID(), campaignId, otherCampaignItemId,
                        otherShutdownId, shutdownItemId),
                        "fk_repair_campaign_work_item_windows_shutdown_item_owner");
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
    private static UUID insertDepartment(Statement statement) throws Exception {
        UUID id = UUID.randomUUID();
        statement.execute("INSERT INTO departments(id,code,name,type,is_deleted,created_at,updated_at) VALUES ('"
                + id + "','D-PG','PG department','WORKSHOP',false,now(),now())");
        return id;
    }
    private static UUID insertEquipment(Statement statement) throws Exception {
        UUID typeId = UUID.randomUUID(); UUID id = UUID.randomUUID();
        statement.execute("INSERT INTO equipment_types(id,code,name,category,is_deleted,created_at,updated_at) VALUES ('"
                + typeId + "','TYPE-T3','Task3 type','PRODUCTION_EQUIPMENT',false,now(),now())");
        statement.execute("INSERT INTO equipment(id,equipment_type_id,code,name,inventory_number,category,status,"
                + "is_deleted,created_at,updated_at) VALUES ('" + id + "','" + typeId
                + "','EQ-T3','Task3 equipment','INV-T3','PRODUCTION_EQUIPMENT','ACTIVE',false,now(),now())");
        return id;
    }
    private static UUID insertCampaign(Statement statement, UUID departmentId, String code) throws Exception {
        UUID id = UUID.randomUUID();
        statement.execute("INSERT INTO repair_campaigns(id,code,name,status,start_date,end_date,total_budget,total_actual,"
                + "currency_code,scope_type,department_id,version,closure_version,is_deleted,created_at,updated_at) VALUES ('"
                + id + "','" + code + "','PG campaign','DRAFT',current_date,current_date,0,0,'UZS','CUSTOM','"
                + departmentId + "',0,0,false,now(),now())");
        return id;
    }
    private static UUID insertShutdown(Statement statement, UUID departmentId, String code) throws Exception {
        UUID id = UUID.randomUUID();
        statement.execute("INSERT INTO planned_shutdowns(id,name,start_at,end_at,reason,status,department_id,code,"
                + "planned_start_at,planned_end_at,is_deleted,created_at,updated_at) VALUES ('" + id
                + "','PG shutdown',now(),now()+interval '1 hour','probe','DRAFT','" + departmentId + "','"
                + code + "',now(),now()+interval '1 hour',false,now(),now())");
        return id;
    }
    private static UUID insertCampaignItem(Statement statement, UUID campaignId, UUID equipmentId, int order) throws Exception {
        UUID id = UUID.randomUUID();
        statement.execute("INSERT INTO repair_campaign_work_items(id,repair_campaign_id,source_type,equipment_id,title,"
                + "status,order_number,is_deleted,created_at,updated_at) VALUES ('" + id + "','" + campaignId
                + "','MANUAL','" + equipmentId + "','PG item','PENDING'," + order + ",false,now(),now())");
        return id;
    }
    private static UUID insertShutdownItem(Statement statement, UUID shutdownId, UUID equipmentId, int order) throws Exception {
        UUID id = UUID.randomUUID();
        statement.execute("INSERT INTO planned_shutdown_work_items(id,planned_shutdown_id,source_type,equipment_id,title,"
                + "order_number,status,is_deleted,created_at,updated_at) VALUES ('" + id + "','" + shutdownId
                + "','MANUAL','" + equipmentId + "','PG item'," + order + ",'PENDING',false,now(),now())");
        return id;
    }
    private static void insertLink(Statement statement, UUID id, UUID campaignId, UUID shutdownId) throws Exception {
        statement.execute("INSERT INTO planned_shutdown_campaigns(id,planned_shutdown_id,repair_campaign_id,is_deleted,"
                + "created_at,updated_at) VALUES ('" + id + "','" + shutdownId + "','" + campaignId
                + "',false,now(),now())");
    }
    private static void insertWindow(Statement statement, UUID id, UUID campaignId, UUID campaignItemId,
                                     UUID shutdownId, UUID shutdownItemId) throws Exception {
        statement.execute("INSERT INTO repair_campaign_work_item_windows(id,repair_campaign_id,"
                + "repair_campaign_work_item_id,planned_shutdown_id,shutdown_work_item_id,is_deleted,created_at,updated_at)"
                + " VALUES ('" + id + "','" + campaignId + "','" + campaignItemId + "','" + shutdownId + "','"
                + shutdownItemId + "',false,now(),now())");
    }
    private static void assertConstraint(SqlCall call, String constraint) {
        assertThatThrownBy(call::run).hasMessageContaining(constraint);
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
    @FunctionalInterface
    private interface SqlCall { void run() throws Exception; }
}
