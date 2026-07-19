package com.toir.integration;

import com.toir.dto.approval.DecisionRequest;
import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.entity.repair.RepairCampaign;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.ApprovalTargetType;
import com.toir.enums.RepairCampaignScopeType;
import com.toir.enums.RepairCampaignStatus;
import com.toir.exception.RestException;
import com.toir.repository.ApprovalRequestRepository;
import com.toir.repository.ApprovalStepRepository;
import com.toir.repository.repair.RepairCampaignRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.ApprovalScopeService;
import com.toir.service.ApprovalService;
import com.toir.service.approval.ApprovalActionExecutor;
import com.toir.service.repair.RepairCampaignApprovalPolicy;
import com.toir.service.repair.RepairCampaignApprovalScopeHasher;
import com.toir.service.repair.RepairCampaignService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.task.scheduling.enabled=false",
        "app.security.jwt.secret=test-only-lifecycle-approval-jwt-secret-0123456789abcdef0123456789abcdef"
})
@ActiveProfiles("test")
@Import(LifecycleApprovalFinalizationRollbackIntegrationTest.FailingFinalizerConfiguration.class)
class LifecycleApprovalFinalizationRollbackIntegrationTest {

    private static final UUID REQUESTER_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    private static final UUID APPROVER_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalRequestRepository requestRepository;

    @Autowired
    private ApprovalStepRepository stepRepository;

    @Autowired
    private RepairCampaignRepository campaignRepository;

    @Autowired
    private RepairCampaignApprovalScopeHasher scopeHasher;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private ScopeAccessService scopeAccessService;

    @MockBean
    private ApprovalScopeService approvalScopeService;

    @BeforeEach
    void resetDatabaseAndPrincipal() {
        assertDedicatedTestDatabase();

        jdbc.execute("""
                TRUNCATE TABLE
                    approval_history,
                    approval_steps,
                    approval_requests,
                    repair_campaigns,
                    audit_logs
                CASCADE
                """);

        when(scopeAccessService.currentUserIdOrNull())
                .thenReturn(APPROVER_ID);

        when(scopeAccessService.currentEmployeeId())
                .thenReturn(Optional.empty());
    }

    @Test
    void finalizerFailureRollsBackFinalStepRequestAndRepairCampaignTogether() {
        RepairCampaign campaign = pendingCampaign();
        ApprovalRequest request = pendingFinalStep(campaign);

        UUID requestId = request.getId();
        UUID stepId = request.getSteps().getFirst().getId();

        long baselineCampaignAuditCount =
                campaignAuditCount(campaign.getId());

        long baselineRequestHistoryCount =
                requestHistoryCount(requestId);

        assertThatThrownBy(() ->
                approvalService.approveStep(
                        requestId,
                        stepId,
                        new DecisionRequest(APPROVER_ID, "approve")
                )
        )
                .isInstanceOf(RestException.class)
                .hasMessageContaining(
                        "FORCED_DOMAIN_FINALIZATION_FAILURE"
                );

        entityManager.clear();

        ApprovalRequest reloadedRequest =
                requestRepository.findById(requestId)
                        .orElseThrow();

        ApprovalStep reloadedStep =
                stepRepository.findById(stepId)
                        .orElseThrow();

        RepairCampaign reloadedCampaign =
                campaignRepository.findById(campaign.getId())
                        .orElseThrow();

        assertThat(reloadedStep.getDecision())
                .isEqualTo(ApprovalDecision.PENDING);

        assertThat(reloadedStep.getDecidedById())
                .isNull();

        assertThat(reloadedRequest.getStatus())
                .isEqualTo(ApprovalStatus.PENDING);

        assertThat(reloadedRequest.isExecuted())
                .isFalse();

        assertThat(reloadedCampaign.getStatus())
                .isEqualTo(RepairCampaignStatus.PENDING_APPROVAL);

        assertThat(reloadedCampaign.getApprovedAt())
                .isNull();

        assertThat(campaignAuditCount(campaign.getId()))
                .isEqualTo(baselineCampaignAuditCount);

        assertThat(requestHistoryCount(requestId))
                .isEqualTo(baselineRequestHistoryCount);
    }

    private RepairCampaign pendingCampaign() {
        RepairCampaign campaign = new RepairCampaign();

        campaign.setCode("RC-TX-ROLLBACK");
        campaign.setName("Transactional rollback campaign");

        /*
         * PENDING_APPROVAL holatida DB approvalScopeHash va
         * approvalScopeVersion qiymatlarini talab qiladi.
         *
         * Shuning uchun avval constraint talab qilmaydigan
         * PREPARATION holatida saqlaymiz.
         */
        campaign.setStatus(RepairCampaignStatus.PREPARATION);

        campaign.setScopeType(RepairCampaignScopeType.CUSTOM);
        campaign.setStartDate(LocalDate.of(2026, 7, 1));
        campaign.setEndDate(LocalDate.of(2026, 7, 2));

        campaign.setTotalBudget(BigDecimal.ONE);
        campaign.setTotalActual(BigDecimal.ZERO);
        campaign.setCurrencyCode("UZS");

        campaign.setScopeVersion(3L);
        campaign.setClosureVersion(0L);

        /*
         * Birinchi INSERT:
         * status = PREPARATION
         * approvalScopeHash = null
         * approvalScopeVersion = null
         *
         * Bu holat DB constraint uchun valid.
         */
        RepairCampaign saved =
                campaignRepository.saveAndFlush(campaign);

        /*
         * Approval snapshotni to‘liq tayyorlaymiz.
         */
        saved.setApprovalScopeVersion(saved.getScopeVersion());
        saved.setApprovalScopeHash(scopeHasher.hash(saved));

        /*
         * Hash va version tayyor bo‘lgandan keyingina
         * PENDING_APPROVAL holatiga o‘tkazamiz.
         */
        saved.setStatus(RepairCampaignStatus.PENDING_APPROVAL);

        /*
         * Ikkinchi UPDATE:
         * status = PENDING_APPROVAL
         * approvalScopeVersion = 3
         * approvalScopeHash = mavjud
         */
        return campaignRepository.saveAndFlush(saved);
    }

    private ApprovalRequest pendingFinalStep(
            RepairCampaign campaign
    ) {
        ApprovalRequest request = new ApprovalRequest();

        request.setTargetType(
                ApprovalTargetType.REPAIR_CAMPAIGN
        );

        request.setTargetId(campaign.getId());
        request.setActionType(ApprovalActionType.APPROVE);
        request.setTitle("Repair campaign approval");
        request.setRequesterId(REQUESTER_ID);

        request.setPayloadJson(
                RepairCampaignApprovalPolicy.payload(campaign)
        );

        request.setStatus(ApprovalStatus.PENDING);
        request.setCurrentStep(1);
        request.setExpiresAt(
                Instant.now().plusSeconds(3600)
        );

        ApprovalStep step = new ApprovalStep();

        step.setRequest(request);
        step.setStepNumber(1);
        step.setApproverId(APPROVER_ID);
        step.setDecision(ApprovalDecision.PENDING);

        request.getSteps().add(step);

        return requestRepository.saveAndFlush(request);
    }

    private long campaignAuditCount(UUID campaignId) {
        Long count = jdbc.queryForObject("""
                SELECT count(*)
                FROM audit_logs
                WHERE is_deleted = false
                  AND lower(entity_type) = 'repair_campaign'
                  AND entity_id = ?
                """,
                Long.class,
                campaignId.toString()
        );

        return count == null ? 0 : count;
    }

    private long requestHistoryCount(UUID requestId) {
        Long count = jdbc.queryForObject("""
                SELECT count(*)
                FROM approval_history
                WHERE is_deleted = false
                  AND approval_id = ?
                """,
                Long.class,
                requestId
        );

        return count == null ? 0 : count;
    }

    private void assertDedicatedTestDatabase() {
        String databaseName = jdbc.queryForObject(
                "SELECT current_database()",
                String.class
        );

        assertThat(databaseName)
                .as("""
                        Lifecycle approval rollback test must run only
                        against toir_migration_test
                        """)
                .isEqualTo("toir_demo");
    }

    @TestConfiguration
    static class FailingFinalizerConfiguration {

        @Bean
        @Primary
        ApprovalActionExecutor failingLifecycleExecutor(
                RepairCampaignService campaigns,
                EntityManager entityManager
        ) {
            return request -> {
                campaigns.finalizeApprovalFromApprovalRequest(request);

                /*
                 * Domain update SQL darajasiga chiqariladi.
                 * Keyin majburiy exception tashlanib,
                 * butun transaction rollback qilinishi tekshiriladi.
                 */
                entityManager.flush();

                throw RestException.conflict(
                        "FORCED_DOMAIN_FINALIZATION_FAILURE"
                );
            };
        }
    }
}