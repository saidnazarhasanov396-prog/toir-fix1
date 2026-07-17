package com.toir.repository;

import com.toir.entity.ApprovalRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ApprovalRequestRepositoryPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    ApprovalRequestRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void returnsExactEffectiveApprovedRowsWithIdDescendingTieBreak() {
        UUID targetId = UUID.randomUUID();
        Instant tiedCreatedAt = Instant.parse("2026-07-17T12:00:00Z");
        UUID lowerId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID higherId = UUID.fromString("f0000000-0000-0000-0000-000000000002");

        insert(lowerId, tiedCreatedAt, false, "PLANNED_SHUTDOWN", targetId,
                "PLANNED_SHUTDOWN", targetId, "APPROVE", "APPROVED");
        insert(higherId, tiedCreatedAt, false, "PLANNED_SHUTDOWN", targetId,
                null, null, null, "APPROVED");
        insert(UUID.randomUUID(), tiedCreatedAt.plusSeconds(1), false, "REPAIR_CAMPAIGN", targetId,
                "REPAIR_CAMPAIGN", targetId, "APPROVE", "APPROVED");
        insert(UUID.randomUUID(), tiedCreatedAt.plusSeconds(1), false, "PLANNED_SHUTDOWN", UUID.randomUUID(),
                "PLANNED_SHUTDOWN", UUID.randomUUID(), "APPROVE", "APPROVED");
        insert(UUID.randomUUID(), tiedCreatedAt.plusSeconds(1), false, "PLANNED_SHUTDOWN", targetId,
                "PLANNED_SHUTDOWN", targetId, "REJECT", "APPROVED");
        insert(UUID.randomUUID(), tiedCreatedAt.plusSeconds(1), false, "PLANNED_SHUTDOWN", targetId,
                "PLANNED_SHUTDOWN", targetId, "APPROVE", "PENDING");
        insert(UUID.randomUUID(), tiedCreatedAt.plusSeconds(1), true, "PLANNED_SHUTDOWN", targetId,
                "PLANNED_SHUTDOWN", targetId, "APPROVE", "APPROVED");

        List<ApprovalRequest> requests = repository
                .findAllApprovedByTargetAndActionOrderByCreatedAtDescIdDesc(
                        "PLANNED_SHUTDOWN", targetId, "APPROVE", "APPROVED");

        assertThat(requests).extracting(ApprovalRequest::getId).containsExactly(higherId, lowerId);
        assertThat(requests.getFirst().getTargetType()).isNull();
        assertThat(requests.getFirst().getActionType()).isNull();
        assertThat(requests.getFirst().getDocumentType()).isEqualTo("PLANNED_SHUTDOWN");
        assertThat(requests.getFirst().getDocumentId()).isEqualTo(targetId);
    }

    private void insert(UUID id,
                        Instant createdAt,
                        boolean deleted,
                        String documentType,
                        UUID documentId,
                        String targetType,
                        UUID targetId,
                        String actionType,
                        String status) {
        jdbc.update("""
                        INSERT INTO approval_requests (
                            id, created_at, updated_at, is_deleted,
                            document_type, document_id, target_type, target_id, action_type,
                            title, requester_id, status, current_step, executed
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id, createdAt, createdAt, deleted,
                documentType, documentId, targetType, targetId, actionType,
                "Runtime approval " + id, UUID.randomUUID(), status, 1, false);
    }
}
