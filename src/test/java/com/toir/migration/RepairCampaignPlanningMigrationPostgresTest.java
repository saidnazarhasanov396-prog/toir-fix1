package com.toir.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepairCampaignPlanningMigrationPostgresTest {
    @Test
    void postgres17FullChainEnforcesV4PlanningContractsAndSerializesResourceRace() throws Exception {
        Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("TOIR_LOCAL_PG17")));
        String database = "task4_" + UUID.randomUUID().toString().replace("-", "");
        String admin = env("TOIR_LOCAL_PG_URL", "jdbc:postgresql://localhost:5432/postgres");
        String url = dbUrl(admin, database);
        try (Connection connection = conn(admin); Statement sql = connection.createStatement()) {
            try (ResultSet version = sql.executeQuery("SHOW server_version_num")) {
                assertThat(version.next()).isTrue();
                assertThat(version.getInt(1)).isBetween(170000, 179999);
            }
            sql.execute("CREATE DATABASE " + database);
        }

        try {
            assertThat(Flyway.configure().dataSource(url, user(), pass()).locations("classpath:db/migration")
                    .baselineOnMigrate(true).baselineVersion("0").load().migrate().success).isTrue();
            Fixture fixture = seedAndAssertConstraints(url);
            assertCanonicalResourceLockSerializesOverlapRace(url, fixture);
        } finally {
            try (Connection connection = conn(admin); Statement sql = connection.createStatement()) {
                sql.execute("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
            }
        }
    }

    private Fixture seedAndAssertConstraints(String url) throws Exception {
        try (Connection connection = conn(url); Statement sql = connection.createStatement()) {
            UUID department = UUID.randomUUID(), type = UUID.randomUUID(), equipment = UUID.randomUUID();
            UUID campaign = UUID.randomUUID(), otherCampaign = UUID.randomUUID();
            UUID first = UUID.randomUUID(), second = UUID.randomUUID(), otherItem = UUID.randomUUID();
            UUID employee = UUID.randomUUID(), brigade = UUID.randomUUID(), counteragent = UUID.randomUUID();
            sql.execute("INSERT INTO departments(id,code,name,type,is_deleted,created_at,updated_at) VALUES ('" + department + "','D4','D4','WORKSHOP',false,now(),now())");
            sql.execute("INSERT INTO equipment_types(id,code,name,category,is_deleted,created_at,updated_at) VALUES ('" + type + "','T4','T4','PRODUCTION_EQUIPMENT',false,now(),now())");
            sql.execute("INSERT INTO equipment(id,equipment_type_id,code,name,inventory_number,category,status,is_deleted,created_at,updated_at) VALUES ('" + equipment + "','" + type + "','E4','E4','I4','PRODUCTION_EQUIPMENT','ACTIVE',false,now(),now())");
            campaign(sql, campaign, department, "RC4");
            campaign(sql, otherCampaign, department, "RC4B");
            item(sql, first, campaign, equipment, 0, "CRITICAL");
            item(sql, second, campaign, equipment, 1, "MEDIUM");
            item(sql, otherItem, otherCampaign, equipment, 0, "HIGH");
            sql.execute("INSERT INTO hr_employees(id,personnel_number,first_name,last_name,\"position\",hire_date,is_active,is_deleted,department_id,created_at,updated_at) VALUES ('" + employee + "','P4','First','Last','welder',current_date,true,false,'" + department + "',now(),now())");
            sql.execute("INSERT INTO brigades(id,code,name,specialization,is_active,is_deleted,department_id,created_at,updated_at) VALUES ('" + brigade + "','B4','B4','mechanical',true,false,'" + department + "',now(),now())");
            sql.execute("INSERT INTO counteragents(id,code,name,status,is_deleted,created_at,updated_at) VALUES ('" + counteragent + "','C4','C4','ACTIVE',false,now(),now())");

            edge(sql, UUID.randomUUID(), campaign, first, second);
            constraint(() -> edge(sql, UUID.randomUUID(), campaign, first, second), "uq_repair_campaign_dependencies_active_edge");
            constraint(() -> edge(sql, UUID.randomUUID(), campaign, first, first), "chk_repair_campaign_dependency_not_self");
            constraint(() -> edge(sql, UUID.randomUUID(), campaign, UUID.randomUUID(), second), "fk_repair_campaign_dependency_predecessor");
            constraint(() -> item(sql, UUID.randomUUID(), campaign, equipment, 3, "URGENT"), "chk_repair_campaign_work_item_priority");
            constraint(() -> assignment(sql, UUID.randomUUID(), campaign, first, null, null, null, "A", "now()", "now()+interval '1 hour'"), "chk_repair_campaign_resource_exactly_one");
            constraint(() -> assignment(sql, UUID.randomUUID(), campaign, first, employee, null, null, "A", "now()", "now()"), "chk_repair_campaign_resource_window");
            constraint(() -> assignment(sql, UUID.randomUUID(), campaign, first, employee, null, null, "   ", "now()", "now()+interval '1 hour'"), "chk_repair_campaign_resource_shift");
            constraint(() -> assignment(sql, UUID.randomUUID(), otherCampaign, first, employee, null, null, "A", "now()", "now()+interval '1 hour'"), "fk_repair_campaign_resource_work_item");

            UUID employeeAssignment = UUID.randomUUID();
            assignment(sql, employeeAssignment, campaign, first, employee, null, null, "A", "timestamptz '2030-01-01 09:00:00Z'", "timestamptz '2030-01-01 10:00:00Z'");
            assignment(sql, UUID.randomUUID(), campaign, first, null, brigade, null, "B", "now()+interval '2 days'", "now()+interval '2 days 1 hour'");
            assignment(sql, UUID.randomUUID(), campaign, first, null, null, counteragent, "C", "now()+interval '3 days'", "now()+interval '3 days 1 hour'");
            constraint(() -> assignment(sql, UUID.randomUUID(), campaign, first, employee, null, null, "A", "timestamptz '2030-01-01 09:00:00Z'", "timestamptz '2030-01-01 10:00:00Z'"), "uq_repair_campaign_resources_active_identity");
            sql.execute("UPDATE repair_campaign_resource_assignments SET is_deleted=true WHERE id='" + employeeAssignment + "'");
            assignment(sql, UUID.randomUUID(), campaign, first, employee, null, null, "A", "timestamptz '2030-01-01 09:00:00Z'", "timestamptz '2030-01-01 10:00:00Z'");
            assignment(sql, UUID.randomUUID(), campaign, second, employee, null, null, "A", "timestamptz '2030-01-01 10:00:00Z'", "timestamptz '2030-01-01 11:00:00Z'");

            try (ResultSet result = sql.executeQuery("UPDATE repair_campaigns SET version=version+1 WHERE id='" + campaign + "' RETURNING version")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong(1)).isEqualTo(1L);
            }
            return new Fixture(otherCampaign, otherItem, employee);
        }
    }

    private void assertCanonicalResourceLockSerializesOverlapRace(String url, Fixture fixture) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch firstHasLock = new CountDownLatch(1);
        try (Connection first = conn(url)) {
            first.setAutoCommit(false);
            lockEmployee(first, fixture.employee());
            assertThat(overlapCount(first, fixture.employee())).isZero();
            firstHasLock.countDown();
            Future<Integer> contender = executor.submit(() -> {
                assertThat(firstHasLock.await(2, TimeUnit.SECONDS)).isTrue();
                try (Connection second = conn(url)) {
                    second.setAutoCommit(false);
                    lockEmployee(second, fixture.employee());
                    int visible = overlapCount(second, fixture.employee());
                    second.rollback();
                    return visible;
                }
            });
            Thread.sleep(200);
            assertThat(contender.isDone()).as("contender waits on the canonical employee row").isFalse();
            try (Statement sql = first.createStatement()) {
                assignment(sql, UUID.randomUUID(), fixture.campaign(), fixture.item(), fixture.employee(), null, null,
                        "RACE", "now()+interval '10 days'", "now()+interval '10 days 1 hour'");
            }
            first.commit();
            assertThat(contender.get(5, TimeUnit.SECONDS)).as("contender observes committed global overlap").isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private void lockEmployee(Connection connection, UUID employee) throws Exception {
        try (Statement sql = connection.createStatement(); ResultSet result = sql.executeQuery("SELECT id FROM hr_employees WHERE id='" + employee + "' FOR UPDATE")) {
            assertThat(result.next()).isTrue();
        }
    }

    private int overlapCount(Connection connection, UUID employee) throws Exception {
        try (Statement sql = connection.createStatement(); ResultSet result = sql.executeQuery(
                "SELECT count(*) FROM repair_campaign_resource_assignments WHERE employee_id='" + employee + "' AND is_deleted=false AND planned_start_at < now()+interval '10 days 1 hour' AND planned_end_at > now()+interval '10 days'")) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }

    static void campaign(Statement sql, UUID id, UUID department, String code) throws Exception {
        sql.execute("INSERT INTO repair_campaigns(id,code,name,status,start_date,end_date,total_budget,total_actual,currency_code,scope_type,department_id,version,closure_version,is_deleted,created_at,updated_at) VALUES ('" + id + "','" + code + "','" + code + "','DRAFT',current_date,current_date,0,0,'UZS','CUSTOM','" + department + "',0,0,false,now(),now())");
    }

    static void item(Statement sql, UUID id, UUID campaign, UUID equipment, int order, String priority) throws Exception {
        sql.execute("INSERT INTO repair_campaign_work_items(id,repair_campaign_id,source_type,equipment_id,title,status,order_number,priority,is_deleted,created_at,updated_at) VALUES ('" + id + "','" + campaign + "','MANUAL','" + equipment + "','item','PENDING'," + order + ",'" + priority + "',false,now(),now())");
    }

    static void edge(Statement sql, UUID id, UUID campaign, UUID predecessor, UUID successor) throws Exception {
        sql.execute("INSERT INTO repair_campaign_work_dependencies(id,repair_campaign_id,predecessor_work_item_id,successor_work_item_id,is_deleted,created_at,updated_at) VALUES ('" + id + "','" + campaign + "','" + predecessor + "','" + successor + "',false,now(),now())");
    }

    static void assignment(Statement sql, UUID id, UUID campaign, UUID workItem, UUID employee, UUID brigade, UUID counteragent,
                           String shift, String start, String end) throws Exception {
        sql.execute("INSERT INTO repair_campaign_resource_assignments(id,repair_campaign_id,work_item_id,employee_id,brigade_id,counteragent_id,shift_code,planned_start_at,planned_end_at,is_deleted,created_at,updated_at) VALUES ('" + id + "','" + campaign + "','" + workItem + "'," + q(employee) + "," + q(brigade) + "," + q(counteragent) + ",'" + shift + "'," + start + "," + end + ",false,now(),now())");
    }

    static String q(UUID id) { return id == null ? "NULL" : "'" + id + "'"; }
    static void constraint(Call call, String name) { assertThatThrownBy(call::run).hasMessageContaining(name); }
    interface Call { void run() throws Exception; }
    record Fixture(UUID campaign, UUID item, UUID employee) {}
    static Connection conn(String url) throws Exception { return DriverManager.getConnection(url, user(), pass()); }
    static String dbUrl(String url, String database) { int query = url.indexOf('?'); String suffix = query < 0 ? "" : url.substring(query), base = query < 0 ? url : url.substring(0, query); return base.substring(0, base.lastIndexOf('/') + 1) + database + suffix; }
    static String user() { return env("TOIR_LOCAL_PG_USER", "postgres"); }
    static String pass() { return env("TOIR_LOCAL_PG_PASSWORD", ""); }
    static String env(String name, String fallback) { String value = System.getenv(name); return value == null ? fallback : value; }
}
