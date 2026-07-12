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

class RepairCampaignApprovalRouteMigrationPostgresTest {

    @Test
    void postgres17FullChainSeedsSevenStepRouteAndScopeConstraint() throws Exception {
        Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("TOIR_LOCAL_PG17")));
        String database = "task6_" + UUID.randomUUID().toString().replace("-", "");
        String admin = env("TOIR_LOCAL_PG_URL", "jdbc:postgresql://localhost:5432/postgres");
        String url = admin.substring(0, admin.lastIndexOf('/') + 1) + database;
        try (Connection connection = connection(admin); Statement sql = connection.createStatement()) {
            sql.execute("CREATE DATABASE " + database);
        }
        try {
            assertThat(Flyway.configure().dataSource(url, user(), password()).locations("classpath:db/migration")
                    .baselineOnMigrate(true).baselineVersion("0").load().migrate().success).isTrue();
            try (Connection connection = connection(url); Statement sql = connection.createStatement()) {
                assertThat(count(sql, "select count(*) from approval_template_steps s join approval_templates t on t.id=s.template_id where t.code='REPAIR_CAMPAIGN_APPROVAL' and s.is_deleted=false"))
                        .isEqualTo(7);
                assertThat(count(sql, "select count(*) from roles where code in ('REPAIR_CAMPAIGN_CHIEF_MECHANIC_APPROVER','REPAIR_CAMPAIGN_PRODUCTION_APPROVER','REPAIR_CAMPAIGN_WAREHOUSE_APPROVER','REPAIR_CAMPAIGN_PROCUREMENT_APPROVER','REPAIR_CAMPAIGN_FINANCE_APPROVER','REPAIR_CAMPAIGN_HSE_APPROVER','REPAIR_CAMPAIGN_CHIEF_ENGINEER_APPROVER') and is_deleted=false"))
                        .isEqualTo(7);
                UUID campaign = UUID.randomUUID();
                sql.execute("insert into repair_campaigns(id,code,name,status,start_date,end_date,total_budget,total_actual,currency_code,scope_type,version,closure_version,scope_version,is_deleted,created_at,updated_at) values ('" + campaign + "','RC6','RC6','DRAFT',current_date,current_date,0,0,'UZS','CUSTOM',0,0,0,false,now(),now())");
                assertThatThrownBy(() -> sql.execute("update repair_campaigns set scope_version=-1 where id='" + campaign + "'"))
                        .hasMessageContaining("chk_repair_campaign_scope_version_non_negative");
            }
        } finally {
            try (Connection connection = connection(admin); Statement sql = connection.createStatement()) {
                sql.execute("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
            }
        }
    }

    private static long count(Statement sql, String query) throws Exception {
        try (ResultSet result = sql.executeQuery(query)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }
    private static Connection connection(String url) throws Exception { return DriverManager.getConnection(url, user(), password()); }
    private static String user() { return env("TOIR_LOCAL_PG_USER", "postgres"); }
    private static String password() { return env("TOIR_LOCAL_PG_PASSWORD", ""); }
    private static String env(String key, String fallback) { String value = System.getenv(key); return value == null ? fallback : value; }
}
