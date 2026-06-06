package com.toir.bootstrap;

import com.toir.ToirApplication;
import com.toir.entity.users.BrigadeMember;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.projects.BrigadeMemberRepository;
import com.toir.repository.projects.BrigadeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class SeedProfileStartupSmokeTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeEach
    void resetDatabase() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS public CASCADE");
            statement.execute("CREATE SCHEMA public");
        }
    }

    @Test
    void startupWithDevProfileDoesNotRunDemoSeeders() {
        try (ConfigurableApplicationContext context = startApplication("dev")) {
            DepartmentRepository departmentRepository = context.getBean(DepartmentRepository.class);
            BrigadeRepository brigadeRepository = context.getBean(BrigadeRepository.class);

            assertThat(departmentRepository.countByIsDeletedFalse()).isZero();
            assertThat(brigadeRepository.countByIsDeletedFalse()).isZero();
        }
    }

    @Test
    void startupWithDevAndDemoSeedProfilesRunsDemoSeeders() {
        try (ConfigurableApplicationContext context = startApplication("dev,demo-seed")) {
            DepartmentRepository departmentRepository = context.getBean(DepartmentRepository.class);
            BrigadeRepository brigadeRepository = context.getBean(BrigadeRepository.class);
            BrigadeMemberRepository brigadeMemberRepository = context.getBean(BrigadeMemberRepository.class);
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);

            assertThat(departmentRepository.countByIsDeletedFalse()).isGreaterThan(0L);
            assertThat(brigadeRepository.countByIsDeletedFalse()).isGreaterThan(0L);

            BrigadeMember member = brigadeMemberRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                    .findFirst()
                    .orElseThrow();
            assertThat(member.getQualifications()).isNotEmpty();

            String jsonbType = jdbcTemplate.queryForObject(
                    "SELECT jsonb_typeof(qualifications) FROM brigade_members WHERE id = ?",
                    String.class,
                    member.getId()
            );
            assertThat(jsonbType).isEqualTo("array");
        }
    }

    @Test
    void startupWithDevAndDemoSeedProfilesSeedsExactP0DemoAssetsIdempotently() {
        Map<String, Long> firstCounts;
        try (ConfigurableApplicationContext context = startApplication("dev,demo-seed")) {
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            assertExactP0DemoAssets(jdbcTemplate);
            firstCounts = p0NaturalKeyCounts(jdbcTemplate);
        }

        try (ConfigurableApplicationContext context = startApplication("dev,demo-seed")) {
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            assertExactP0DemoAssets(jdbcTemplate);
            assertThat(p0NaturalKeyCounts(jdbcTemplate)).isEqualTo(firstCounts);
        }
    }

    @Test
    void startupWithProdProfileDoesNotRunDemoSeeders() {
        try (ConfigurableApplicationContext context = startApplication("prod")) {
            DepartmentRepository departmentRepository = context.getBean(DepartmentRepository.class);
            BrigadeRepository brigadeRepository = context.getBean(BrigadeRepository.class);

            assertThat(departmentRepository.countByIsDeletedFalse()).isZero();
            assertThat(brigadeRepository.countByIsDeletedFalse()).isZero();
        }
    }

    private ConfigurableApplicationContext startApplication(String profilesCsv) {
        return new SpringApplicationBuilder(ToirApplication.class)
                .profiles(profilesCsv.split(","))
                .properties(
                        "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "spring.datasource.username=" + POSTGRES.getUsername(),
                        "spring.datasource.password=" + POSTGRES.getPassword(),
                        "spring.datasource.driver-class-name=org.postgresql.Driver",
                        "spring.jpa.hibernate.ddl-auto=validate",
                        "spring.jpa.open-in-view=false",
                        "spring.flyway.enabled=true",
                        "spring.flyway.baseline-on-migrate=true",
                        "spring.flyway.baseline-version=0",
                        "spring.flyway.locations=classpath:db/migration",
                        "spring.flyway.validate-on-migrate=true",
                        "spring.main.web-application-type=servlet",
                        "spring.task.scheduling.enabled=false",
                        "server.port=0",
                        "springdoc.cache.disabled=true"
                )
                .run();
    }

    private void assertExactP0DemoAssets(JdbcTemplate jdbcTemplate) {
        assertThat(count(jdbcTemplate, """
                SELECT count(*)
                FROM equipment
                WHERE code IN ('AUTO-PUMP-A1', 'AUTO-PUMP-A2', 'AUTO-PUMP-A3-NOMETER')
                  AND is_deleted = false
                """)).isEqualTo(3L);
        assertThat(count(jdbcTemplate, """
                SELECT count(*)
                FROM equipment_meters m
                JOIN equipment e ON e.id = m.equipment_id
                WHERE e.code IN ('AUTO-PUMP-A1', 'AUTO-PUMP-A2')
                  AND m.meter_type = 'CYCLES'
                  AND m.is_active = true
                  AND m.is_deleted = false
                """)).isEqualTo(2L);
        assertThat(count(jdbcTemplate, """
                SELECT count(*)
                FROM equipment_meters m
                JOIN equipment e ON e.id = m.equipment_id
                WHERE e.code = 'AUTO-PUMP-A3-NOMETER'
                  AND m.is_active = true
                  AND m.is_deleted = false
                """)).isZero();
        assertThat(count(jdbcTemplate, """
                SELECT count(*)
                FROM maintenance_operations o
                JOIN maintenance_templates t ON t.id = o.template_id
                WHERE t.code = 'AUTO-PUMP-PM-2026'
                  AND o.is_deleted = false
                """)).isGreaterThanOrEqualTo(3L);
        assertThat(count(jdbcTemplate, """
                SELECT count(*)
                FROM maintenance_template_spare_part_requirements r
                JOIN maintenance_templates t ON t.id = r.template_id
                WHERE t.code = 'AUTO-PUMP-PM-2026'
                  AND r.is_deleted = false
                  AND r.is_active = true
                """)).isGreaterThanOrEqualTo(2L);
        assertThat(count(jdbcTemplate, """
                SELECT count(*)
                FROM maintenance_due_events d
                JOIN equipment e ON e.id = d.equipment_id
                WHERE e.code = 'AUTO-PUMP-A3-NOMETER'
                  AND d.due_status = 'BLOCKED'
                  AND d.created_work_order_id IS NULL
                  AND d.is_deleted = false
                """)).isEqualTo(1L);
        assertThat(count(jdbcTemplate, """
                SELECT count(*)
                FROM work_orders wo
                JOIN equipment e ON e.id = wo.equipment_id
                WHERE e.code = 'AUTO-PUMP-A1'
                  AND wo.number = 'AUTO-WO-A1-HISTORY-2026-05'
                  AND wo.is_deleted = false
                """)).isEqualTo(1L);
        assertThat(count(jdbcTemplate, """
                SELECT count(*)
                FROM actual_costs
                WHERE source_type IN ('LABOR_ENTRY', 'MATERIAL_ISSUE')
                  AND source_id IS NOT NULL
                  AND is_deleted = false
                """)).isGreaterThanOrEqualTo(2L);
    }

    private Map<String, Long> p0NaturalKeyCounts(JdbcTemplate jdbcTemplate) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("equipment", count(jdbcTemplate, """
                SELECT count(*) FROM equipment
                WHERE code IN ('AUTO-PUMP-A1', 'AUTO-PUMP-A2', 'AUTO-PUMP-A3-NOMETER')
                  AND is_deleted = false
                """));
        counts.put("meters", count(jdbcTemplate, """
                SELECT count(*)
                FROM equipment_meters m
                JOIN equipment e ON e.id = m.equipment_id
                WHERE e.code IN ('AUTO-PUMP-A1', 'AUTO-PUMP-A2', 'AUTO-PUMP-A3-NOMETER')
                  AND m.is_deleted = false
                """));
        counts.put("template", count(jdbcTemplate, """
                SELECT count(*) FROM maintenance_templates
                WHERE code = 'AUTO-PUMP-PM-2026'
                  AND is_deleted = false
                """));
        counts.put("operations", count(jdbcTemplate, """
                SELECT count(*)
                FROM maintenance_operations o
                JOIN maintenance_templates t ON t.id = o.template_id
                WHERE t.code = 'AUTO-PUMP-PM-2026'
                  AND o.is_deleted = false
                """));
        counts.put("spareRequirements", count(jdbcTemplate, """
                SELECT count(*)
                FROM maintenance_template_spare_part_requirements r
                JOIN maintenance_templates t ON t.id = r.template_id
                WHERE t.code = 'AUTO-PUMP-PM-2026'
                  AND r.is_deleted = false
                  AND r.is_active = true
                """));
        counts.put("stockRows", count(jdbcTemplate, """
                SELECT count(*)
                FROM warehouse_stocks ws
                WHERE ws.warehouse_id = '00000000-0000-0000-0000-000000030001'::uuid
                  AND ws.spare_part_id IN (
                    '00000000-0000-0000-0000-000000040001'::uuid,
                    '00000000-0000-0000-0000-000000040002'::uuid
                  )
                  AND ws.is_deleted = false
                """));
        counts.put("dueEvents", count(jdbcTemplate, """
                SELECT count(*)
                FROM maintenance_due_events d
                JOIN equipment e ON e.id = d.equipment_id
                WHERE e.code IN ('AUTO-PUMP-A1', 'AUTO-PUMP-A2', 'AUTO-PUMP-A3-NOMETER')
                  AND d.is_deleted = false
                """));
        counts.put("users", count(jdbcTemplate, """
                SELECT count(*) FROM users
                WHERE username IN ('NAV-ppr_engineer', 'NAV-foreman', 'NAV-storekeeper', 'NAV-economist', 'NAV-viewer')
                  AND is_deleted = false
                """));
        counts.put("costs", count(jdbcTemplate, """
                SELECT count(*)
                FROM actual_costs
                WHERE source_type IN ('LABOR_ENTRY', 'MATERIAL_ISSUE')
                  AND source_id IS NOT NULL
                  AND is_deleted = false
                """));
        return counts;
    }

    private Long count(JdbcTemplate jdbcTemplate, String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }
}
