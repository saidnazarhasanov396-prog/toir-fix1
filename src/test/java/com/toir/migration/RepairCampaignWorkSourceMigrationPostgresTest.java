package com.toir.migration;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepairCampaignWorkSourceMigrationPostgresTest {
    private static final Path V1 = Path.of(
            "src/main/resources/db/migration/V20260712_1__repair_campaign_core.sql");
    private static final Path V2 = Path.of(
            "src/main/resources/db/migration/V20260712_2__repair_campaign_work_sources.sql");

    @Test
    void v1ToV2EnforcesActiveIdentityOrderStatusAndRootVersion() throws Exception {
        Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("TOIR_LOCAL_PG17")),
                "Set TOIR_LOCAL_PG17=true to run the local PostgreSQL 17 migration probe");
        String schema = "task2_chain_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
            statement.execute("SET search_path TO " + schema);
            statement.execute("""
                    CREATE TABLE users (id uuid PRIMARY KEY);
                    CREATE TABLE hr_employees (id uuid PRIMARY KEY);
                    CREATE TABLE equipment (id uuid PRIMARY KEY);
                    CREATE TABLE planned_shutdown_work_items (
                        id uuid PRIMARY KEY,
                        source_type varchar(32),
                        CONSTRAINT chk_planned_shutdown_work_items_source_type
                            CHECK (source_type IN ('MANUAL','DEFECT','PPR','WORK_ORDER','REPAIR_CAMPAIGN'))
                    );
                    CREATE TABLE repair_campaigns (
                        id uuid PRIMARY KEY,
                        code varchar(255) NOT NULL,
                        name varchar(255) NOT NULL,
                        department_id uuid,
                        status varchar(255) NOT NULL,
                        start_date date NOT NULL,
                        end_date date NOT NULL,
                        notes text,
                        is_deleted boolean NOT NULL DEFAULT false,
                        created_at timestamptz NOT NULL,
                        updated_at timestamptz NOT NULL,
                        CONSTRAINT repair_campaigns_status_check CHECK (
                            status IN ('DRAFT','APPROVED','IN_PROGRESS','COMPLETED','CLOSED','CANCELLED')),
                        CONSTRAINT repair_campaigns_code_key UNIQUE (code)
                    )
                    """);
            statement.execute(Files.readString(V1));
            statement.execute(Files.readString(V2));

            UUID campaignId = UUID.randomUUID(); UUID equipmentId = UUID.randomUUID();
            UUID sourceId = UUID.randomUUID(); UUID otherSourceId = UUID.randomUUID();
            statement.execute("INSERT INTO repair_campaigns(id,code,name,status,start_date,end_date,created_at,updated_at) "
                    + "VALUES ('" + campaignId + "','RC-PG','PG','DRAFT',current_date,current_date,now(),now())");
            statement.execute("INSERT INTO equipment(id) VALUES ('" + equipmentId + "')");
            UUID firstItem = insert(statement, campaignId, equipmentId, sourceId, 0, "PENDING");

            assertConstraint(() -> insert(statement, campaignId, equipmentId, sourceId, 1, "PENDING"),
                    "uq_repair_campaign_work_items_active_source");
            assertConstraint(() -> insert(statement, campaignId, equipmentId, otherSourceId, 0, "PENDING"),
                    "uq_repair_campaign_work_items_active_order");

            statement.execute("UPDATE repair_campaign_work_items SET is_deleted=true WHERE id='" + firstItem + "'");
            insert(statement, campaignId, equipmentId, sourceId, 0, "REPLAN_REQUIRED");
            assertConstraint(() -> insert(statement, campaignId, equipmentId, UUID.randomUUID(), 2, "CLIENT_FORGED"),
                    "chk_repair_campaign_work_items_status");

            try (ResultSet result = statement.executeQuery(
                    "UPDATE repair_campaigns SET version=version+1 WHERE id='" + campaignId + "' RETURNING version")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong(1)).isEqualTo(1L);
            }
        } finally {
            try (Connection connection = connection(); Statement statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
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

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(
                System.getenv().getOrDefault("TOIR_LOCAL_PG_URL", "jdbc:postgresql://localhost:5432/postgres"),
                System.getenv().getOrDefault("TOIR_LOCAL_PG_USER", "postgres"),
                System.getenv().getOrDefault("TOIR_LOCAL_PG_PASSWORD", ""));
    }

    @FunctionalInterface
    private interface SqlCall { void run() throws SQLException; }
}
