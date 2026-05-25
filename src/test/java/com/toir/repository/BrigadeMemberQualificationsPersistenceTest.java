package com.toir.repository;

import com.toir.entity.users.Brigade;
import com.toir.entity.users.BrigadeMember;
import com.toir.repository.projects.BrigadeMemberRepository;
import com.toir.repository.projects.BrigadeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class BrigadeMemberQualificationsPersistenceTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    BrigadeRepository brigadeRepository;

    @Autowired
    BrigadeMemberRepository brigadeMemberRepository;

    @Autowired
    TestEntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void qualificationsPersistAsJsonbArrayAndReadBackAsList() {
        Brigade brigade = new Brigade();
        brigade.setCode("BR-" + UUID.randomUUID());
        brigade.setName("Demo Brigade");
        brigade = brigadeRepository.save(brigade);

        BrigadeMember member = new BrigadeMember();
        member.setBrigade(brigade);
        member.setUserId(UUID.randomUUID());
        member.setRoleCode("FOREMAN");
        member.setGrade(6);
        member.setQualifications(List.of("WELDING", "MECHANICAL"));
        member = brigadeMemberRepository.saveAndFlush(member);

        entityManager.clear();

        BrigadeMember reloaded = brigadeMemberRepository.findById(member.getId()).orElseThrow();
        assertThat(reloaded.getQualifications()).containsExactly("WELDING", "MECHANICAL");

        String valueType = jdbcTemplate.queryForObject(
                "SELECT jsonb_typeof(qualifications) FROM brigade_members WHERE id = ?",
                String.class,
                member.getId()
        );
        assertThat(valueType).isEqualTo("array");
    }
}
