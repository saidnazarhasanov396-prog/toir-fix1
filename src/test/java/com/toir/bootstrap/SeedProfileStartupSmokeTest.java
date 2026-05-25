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
}
