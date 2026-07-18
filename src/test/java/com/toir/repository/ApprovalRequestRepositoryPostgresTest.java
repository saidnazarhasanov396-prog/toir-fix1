package com.toir.repository;

import com.toir.entity.ApprovalRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ActiveProfiles("test")
class ApprovalRequestRepositoryPostgresTest {

    @Autowired
    private ApprovalRequestRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void returnsExactEffectiveApprovedRowsWithIdDescendingTieBreak() {
        UUID targetId = UUID.randomUUID();

        Instant tiedCreatedAt =
                Instant.parse("2026-07-17T12:00:00Z");

        UUID lowerId =
                UUID.fromString(
                        "10000000-0000-0000-0000-000000000001"
                );

        UUID higherId =
                UUID.fromString(
                        "f0000000-0000-0000-0000-000000000002"
                );

        insert(
                lowerId,
                tiedCreatedAt,
                false,
                "PLANNED_SHUTDOWN",
                targetId,
                "PLANNED_SHUTDOWN",
                targetId,
                "APPROVE",
                "APPROVED"
        );

        insert(
                higherId,
                tiedCreatedAt,
                false,
                "PLANNED_SHUTDOWN",
                targetId,
                null,
                null,
                null,
                "APPROVED"
        );

        insert(
                UUID.randomUUID(),
                tiedCreatedAt.plusSeconds(1),
                false,
                "REPAIR_CAMPAIGN",
                targetId,
                "REPAIR_CAMPAIGN",
                targetId,
                "APPROVE",
                "APPROVED"
        );

        insert(
                UUID.randomUUID(),
                tiedCreatedAt.plusSeconds(1),
                false,
                "PLANNED_SHUTDOWN",
                UUID.randomUUID(),
                "PLANNED_SHUTDOWN",
                UUID.randomUUID(),
                "APPROVE",
                "APPROVED"
        );

        insert(
                UUID.randomUUID(),
                tiedCreatedAt.plusSeconds(1),
                false,
                "PLANNED_SHUTDOWN",
                targetId,
                "PLANNED_SHUTDOWN",
                targetId,
                "REJECT",
                "APPROVED"
        );

        insert(
                UUID.randomUUID(),
                tiedCreatedAt.plusSeconds(1),
                false,
                "PLANNED_SHUTDOWN",
                targetId,
                "PLANNED_SHUTDOWN",
                targetId,
                "APPROVE",
                "PENDING"
        );

        insert(
                UUID.randomUUID(),
                tiedCreatedAt.plusSeconds(1),
                true,
                "PLANNED_SHUTDOWN",
                targetId,
                "PLANNED_SHUTDOWN",
                targetId,
                "APPROVE",
                "APPROVED"
        );

        List<ApprovalRequest> requests =
                repository
                        .findAllApprovedByTargetAndActionOrderByCreatedAtDescIdDesc(
                                "PLANNED_SHUTDOWN",
                                targetId,
                                "APPROVE",
                                "APPROVED"
                        );

        assertThat(requests)
                .extracting(ApprovalRequest::getId)
                .containsExactly(higherId, lowerId);

        ApprovalRequest first = requests.getFirst();

        assertThat(first.getTargetType())
                .isNull();

        assertThat(first.getActionType())
                .isNull();

        assertThat(first.getDocumentType())
                .isEqualTo("PLANNED_SHUTDOWN");

        assertThat(first.getDocumentId())
                .isEqualTo(targetId);
    }

    private void insert(
            UUID id,
            Instant createdAt,
            boolean deleted,
            String documentType,
            UUID documentId,
            String targetType,
            UUID targetId,
            String actionType,
            String status
    ) {
        Timestamp timestamp = Timestamp.from(createdAt);

        jdbc.update("""
                        INSERT INTO approval_requests (
                            id,
                            created_at,
                            updated_at,
                            is_deleted,
                            document_type,
                            document_id,
                            target_type,
                            target_id,
                            action_type,
                            title,
                            requester_id,
                            status,
                            current_step,
                            executed
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                timestamp,
                timestamp,
                deleted,
                documentType,
                documentId,
                targetType,
                targetId,
                actionType,
                "Runtime approval " + id,
                UUID.randomUUID(),
                status,
                1,
                false
        );
    }
}