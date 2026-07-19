package com.toir.integration;

import com.toir.dto.approval.ApprovalRequestDto;
import com.toir.dto.approval.ApprovalRuleDto;
import com.toir.entity.ApprovalTemplate;
import com.toir.entity.ApprovalTemplateStep;
import com.toir.entity.repair.RepairCampaign;
import com.toir.enums.*;
import com.toir.exception.RestException;
import com.toir.repository.ApprovalTemplateRepository;
import com.toir.repository.repair.RepairCampaignRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.ApprovalService;
import com.toir.service.approval.ApprovalRuleService;
import com.toir.service.approval.LifecycleApprovalStartPlan;
import com.toir.service.repair.RepairCampaignApprovalPolicy;
import com.toir.service.repair.RepairCampaignApprovalScopeHasher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.task.scheduling.enabled=false",
        "app.security.jwt.secret=test-only-lifecycle-approval-jwt-secret-0123456789abcdef0123456789abcdef"
})
@ActiveProfiles("test")
class LifecycleApprovalConcurrencyIntegrationTest {

    private static final UUID REQUESTER_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final long TIMEOUT_SECONDS = 15;



    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalRuleService ruleService;

    @Autowired
    private RepairCampaignRepository campaignRepository;

    @Autowired
    private RepairCampaignApprovalScopeHasher scopeHasher;

    @Autowired
    private ApprovalTemplateRepository templateRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockBean
    private ScopeAccessService scopeAccessService;

    private ExecutorService executor;

    @BeforeEach
    void resetDatabaseAndPrincipal() {
        assertDedicatedTestDatabase();

        jdbc.execute("TRUNCATE TABLE approval_template_steps, approval_templates, "
                + "approval_history, approval_steps, approval_requests, repair_campaigns, audit_logs CASCADE");
        jdbc.execute("DROP INDEX IF EXISTS uq_active_lifecycle_approval_template");
        jdbc.execute("""
                CREATE UNIQUE INDEX uq_active_lifecycle_approval_template
                    ON approval_templates (target_type, COALESCE(action_type, 'APPROVE'))
                    WHERE active = true
                      AND is_deleted = false
                      AND target_type IN ('REPAIR_CAMPAIGN', 'PLANNED_SHUTDOWN')
                      AND COALESCE(action_type, 'APPROVE') = 'APPROVE'
                """);
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(REQUESTER_ID);
        when(scopeAccessService.currentEmployeeId()).thenReturn(Optional.empty());
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void shutdownExecutor() throws InterruptedException {
        if (executor != null) {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void concurrentRequestApprovalCreatesAtMostOnePendingRequest() throws Exception {
        activeLifecycleTemplate("RC-CONCURRENT-REQUEST", ApprovalTargetType.REPAIR_CAMPAIGN);
        RepairCampaign campaign = pendingApprovalCampaign();
        String currentPayload = RepairCampaignApprovalPolicy.payload(campaign);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<CallResult<ApprovalRequestDto>> first = executor.submit(() -> requestApproval(
                campaign.getId(), currentPayload, ready, start));
        Future<CallResult<ApprovalRequestDto>> second = executor.submit(() -> requestApproval(
                campaign.getId(), currentPayload, ready, start));

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        List<CallResult<ApprovalRequestDto>> results = List.of(
                first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        List<UUID> pendingIds = pendingRequestIds(ApprovalTargetType.REPAIR_CAMPAIGN, campaign.getId());
        assertThat(pendingIds).hasSize(1);
        assertThat(pendingPayload(pendingIds.getFirst())).isEqualTo(currentPayload);
        assertThat(results).anyMatch(CallResult::successful);
        assertThat(results.stream().filter(CallResult::successful).map(CallResult::value)
                .map(ApprovalRequestDto::id)).containsOnly(pendingIds.getFirst());
        assertThat(results.stream().filter(result -> !result.successful()).map(CallResult::failure))
                .allMatch(RestException.class::isInstance);
    }

    @Test
    void concurrentTemplateActivationLeavesAtMostOneActiveExactTemplate() throws Exception {
        ApprovalTemplate firstTemplate = inactiveLifecycleTemplate("RC-ACTIVATE-A", "REVIEWER_A");
        ApprovalTemplate secondTemplate = inactiveLifecycleTemplate("RC-ACTIVATE-B", "REVIEWER_B");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<SqlResult> first = executor.submit(() -> activateThroughJdbc(firstTemplate.getId(), ready, start));
        Future<SqlResult> second = executor.submit(() -> activateThroughJdbc(secondTemplate.getId(), ready, start));

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        List<SqlResult> results = List.of(
                first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        assertThat(results).filteredOn(SqlResult::committed).hasSize(1);
        assertThat(results).filteredOn(result -> !result.committed()).hasSize(1)
                .first().extracting(SqlResult::sqlState).isEqualTo("23505");
        assertThat(activeExactTemplateCount()).isEqualTo(1);
    }

    @Test
    void uniqueViolationNeverLeaksRawSqlException() throws Exception {
        ApprovalTemplate serviceTemplate = inactiveLifecycleTemplate(
                "REPAIR_CAMPAIGN_APPROVE", "SERVICE_REVIEWER");
        ApprovalTemplate competingTemplate = inactiveLifecycleTemplate(
                "RC-UNCOMMITTED-COMPETITOR", "COMPETING_REVIEWER");
        CountDownLatch serviceReady = new CountDownLatch(1);
        CountDownLatch serviceStart = new CountDownLatch(1);

        try (Connection winner = dataSource.getConnection()) {
            winner.setAutoCommit(false);
            setTransactionTimeouts(winner);
            updateActive(winner, competingTemplate.getId());

            Future<CallResult<ApprovalRuleDto>> loser = executor.submit(() -> activateThroughService(
                    serviceTemplate, serviceReady, serviceStart));
            assertThat(serviceReady.await(5, TimeUnit.SECONDS)).isTrue();
            serviceStart.countDown();
            awaitApprovalTemplateLockWait();
            winner.commit();

            CallResult<ApprovalRuleDto> result = loser.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(result.successful()).isFalse();
            assertThat(result.failure()).isInstanceOf(RestException.class)
                    .isNotInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
                    .hasMessageContaining("MULTIPLE_ACTIVE_TEMPLATES");
        }

        assertThat(activeExactTemplateCount()).isEqualTo(1);
    }

    private CallResult<ApprovalRequestDto> requestApproval(
            UUID campaignId,
            String currentPayload,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            return CallResult.failure(new AssertionError("requestApproval start gate timed out"));
        }
        return inTransaction(() -> {
            LifecycleApprovalStartPlan plan = approvalService.planLifecycleApproval(
                    ApprovalTargetType.REPAIR_CAMPAIGN,
                    campaignId,
                    ApprovalActionType.APPROVE,
                    true,
                    currentPayload);
            return approvalService.materializeLifecycleApproval(
                    plan,
                    REQUESTER_ID,
                    "Concurrent Repair Campaign approval",
                    "concurrent request",
                    currentPayload);
        });
    }

    private SqlResult activateThroughJdbc(
            UUID templateId,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            setTransactionTimeouts(connection);
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                connection.rollback();
                return new SqlResult(false, "START_GATE_TIMEOUT");
            }
            try {
                updateActive(connection, templateId);
                connection.commit();
                return new SqlResult(true, null);
            } catch (SQLException failure) {
                connection.rollback();
                return new SqlResult(false, failure.getSQLState());
            }
        }
    }

    private CallResult<ApprovalRuleDto> activateThroughService(
            ApprovalTemplate template,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            return CallResult.failure(new AssertionError("template service start gate timed out"));
        }
        ApprovalRuleDto request = new ApprovalRuleDto(
                template.getId(),
                ApprovalTargetType.REPAIR_CAMPAIGN,
                ApprovalActionType.APPROVE,
                template.getName(),
                1,
                List.of(new ApprovalRuleDto.Step(
                        1, null, null, "SERVICE_REVIEWER", ApprovalRuleDto.ApproverType.ROLE)),
                true);
        return inTransaction(() -> ruleService.updateRule(template.getId(), request));
    }

    private <T> CallResult<T> inTransaction(ThrowingSupplier<T> work) {
        try {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            T result = transaction.execute(status -> {
                jdbc.execute("SET LOCAL lock_timeout = '5s'");
                jdbc.execute("SET LOCAL statement_timeout = '10s'");
                try {
                    return work.get();
                } catch (RuntimeException failure) {
                    throw failure;
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            });
            return CallResult.success(result);
        } catch (Throwable failure) {
            return CallResult.failure(failure);
        }
    }

    private void awaitApprovalTemplateLockWait() throws Exception {
        CountDownLatch boundedPollDelay = new CountDownLatch(1);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject("""
                    SELECT count(*)
                    FROM pg_stat_activity
                    WHERE datname = current_database()
                      AND pid <> pg_backend_pid()
                      AND wait_event_type = 'Lock'
                      AND query ILIKE '%approval_templates%'
                    """, Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            boundedPollDelay.await(25, TimeUnit.MILLISECONDS);
        }
        throw new AssertionError("Service activation did not reach the unique-index lock wait");
    }

    private void setTransactionTimeouts(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET LOCAL lock_timeout = '5s'");
            statement.execute("SET LOCAL statement_timeout = '10s'");
        }
    }

    private void updateActive(Connection connection, UUID templateId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "UPDATE approval_templates SET active = true, updated_at = now() WHERE id = ?")) {
            statement.setObject(1, templateId);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private RepairCampaign pendingApprovalCampaign() {
        RepairCampaign campaign = new RepairCampaign();
        campaign.setCode("RC-CONCURRENT-REQUEST");
        campaign.setName("Concurrent request campaign");

        // Avval constraint talab qilmaydigan statusda saqlaymiz
        campaign.setStatus(RepairCampaignStatus.PREPARATION);

        campaign.setScopeType(RepairCampaignScopeType.CUSTOM);
        campaign.setStartDate(LocalDate.of(2026, 7, 1));
        campaign.setEndDate(LocalDate.of(2026, 7, 2));
        campaign.setTotalBudget(BigDecimal.ONE);
        campaign.setTotalActual(BigDecimal.ZERO);
        campaign.setCurrencyCode("UZS");
        campaign.setScopeVersion(4L);
        campaign.setClosureVersion(0L);

        RepairCampaign saved = campaignRepository.saveAndFlush(campaign);

        saved.setApprovalScopeVersion(saved.getScopeVersion());
        saved.setApprovalScopeHash(scopeHasher.hash(saved));
        saved.setStatus(RepairCampaignStatus.PENDING_APPROVAL);

        return campaignRepository.saveAndFlush(saved);
    }

    private ApprovalTemplate activeLifecycleTemplate(String code, ApprovalTargetType targetType) {
        ApprovalTemplate template = lifecycleTemplate(code, "SYSTEM_ADMIN");
        template.setTargetType(targetType);
        template.setActive(true);
        return templateRepository.saveAndFlush(template);
    }

    private ApprovalTemplate inactiveLifecycleTemplate(String code, String role) {
        return templateRepository.saveAndFlush(lifecycleTemplate(code, role));
    }

    private ApprovalTemplate lifecycleTemplate(String code, String role) {
        ApprovalTemplate template = new ApprovalTemplate();
        template.setCode(code);
        template.setName(code);
        template.setTargetType(ApprovalTargetType.REPAIR_CAMPAIGN);
        template.setActionType(ApprovalActionType.APPROVE);
        template.setRoutePolicy(ApprovalRoutePolicy.ROLE_BASED);
        template.setApproverRole(role);
        template.setActive(false);

        ApprovalTemplateStep step = new ApprovalTemplateStep();
        step.setTemplate(template);
        step.setStepOrder(1);
        step.setApproverRole(role);
        template.getSteps().add(step);
        return template;
    }

    private List<UUID> pendingRequestIds(ApprovalTargetType targetType, UUID targetId) {
        return jdbc.query("""
                SELECT id
                FROM approval_requests
                WHERE is_deleted = false
                  AND status = 'PENDING'
                  AND COALESCE(target_type, document_type) = ?
                  AND COALESCE(target_id, document_id) = ?
                  AND COALESCE(action_type, 'APPROVE') = 'APPROVE'
                ORDER BY id
                """, (rs, rowNum) -> rs.getObject("id", UUID.class), targetType.name(), targetId);
    }

    private String pendingPayload(UUID requestId) {
        return jdbc.queryForObject(
                "SELECT payload_json FROM approval_requests WHERE id = ?",
                String.class,
                requestId);
    }

    private long activeExactTemplateCount() {
        Long count = jdbc.queryForObject("""
                SELECT count(*)
                FROM approval_templates
                WHERE active = true
                  AND is_deleted = false
                  AND target_type = 'REPAIR_CAMPAIGN'
                  AND COALESCE(action_type, 'APPROVE') = 'APPROVE'
                """, Long.class);
        return count == null ? 0 : count;
    }

    private record SqlResult(boolean committed, String sqlState) {
    }

    private record CallResult<T>(T value, Throwable failure) {
        static <T> CallResult<T> success(T value) {
            return new CallResult<>(value, null);
        }

        static <T> CallResult<T> failure(Throwable failure) {
            return new CallResult<>(null, failure);
        }

        boolean successful() {
            return failure == null;
        }
    }

    private void assertDedicatedTestDatabase() {
        String databaseName = jdbc.queryForObject(
                "SELECT current_database()",
                String.class
        );

        assertThat(databaseName)
                .as("Lifecycle concurrency test must run only against toir_migration_test")
                .isEqualTo("toir_demo");
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
