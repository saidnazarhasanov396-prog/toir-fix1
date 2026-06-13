package com.toir.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;

@Testcontainers(disabledWithoutDocker = true)
class ApprovalRequestStatusConstraintMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .validateOnMigrate(true)
                .load()
                .migrate();
    }

    @Test
    void approvalRequestsStatusConstraintAcceptsFailedAndExpired() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        )) {
            assertThatCode(() -> insertApprovalRequest(connection, "FAILED"))
                    .doesNotThrowAnyException();
            assertThatCode(() -> insertApprovalRequest(connection, "EXPIRED"))
                    .doesNotThrowAnyException();
        }
    }

    private void insertApprovalRequest(Connection connection, String status) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO approval_requests (
                    id, created_at, updated_at, is_deleted,
                    document_type, document_id, title, requester_id,
                    status, current_step
                )
                VALUES (?, now(), now(), false, ?, ?, ?, ?, ?, 1)
                """)) {
            UUID id = UUID.randomUUID();
            statement.setObject(1, id);
            statement.setString(2, "WORK_ORDER");
            statement.setObject(3, UUID.randomUUID());
            statement.setString(4, "Approval status constraint test " + status);
            statement.setObject(5, UUID.randomUUID());
            statement.setString(6, status);
            statement.executeUpdate();
        }
    }
}
