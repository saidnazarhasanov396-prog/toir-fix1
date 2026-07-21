package com.toir.service.repair;

import com.toir.dto.repaircampaign.RepairCampaignWorkOrderEligibilityResult;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.BudgetLine;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.entity.repair.RepairCampaign;
import com.toir.entity.repair.RepairCampaignStage;
import com.toir.enums.RepairCampaignScopeType;
import com.toir.enums.RepairCampaignStatus;
import com.toir.enums.RepairCampaignWorkOrderEligibilityCode;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import com.toir.exception.RestException;
import com.toir.repository.projects.BudgetLineRepository;
import com.toir.repository.repair.RepairCampaignDepartmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepairCampaignWorkOrderEligibilityPolicyTest {

    @Mock
    private RepairCampaignDepartmentRepository campaignDepartments;

    @Mock
    private BudgetLineRepository budgetLines;

    @InjectMocks
    private RepairCampaignWorkOrderEligibilityPolicy policy;

    @ParameterizedTest
    @EnumSource(value = WorkOrderType.class, names = {"OVERHAUL", "MEDIUM_REPAIR", "CAPITAL_REPAIR"})
    void allowsEverySupportedCampaignWorkOrderType(WorkOrderType type) {
        Fixture fixture = fixture();
        fixture.workOrder().setType(type);

        assertThat(policy.evaluate(fixture.campaign(), fixture.stage(), fixture.workOrder()))
                .isEqualTo(RepairCampaignWorkOrderEligibilityResult.allowed());
    }

    @Test
    void rejectsUnsupportedTypeWithStableCode() {
        Fixture fixture = fixture();
        fixture.workOrder().setType(WorkOrderType.PLANNED);

        assertThat(policy.evaluate(fixture.campaign(), fixture.stage(), fixture.workOrder()).reasonCode())
                .isEqualTo(RepairCampaignWorkOrderEligibilityCode.REPAIR_CAMPAIGN_WORK_ORDER_TYPE_NOT_ALLOWED);
        assertThatThrownBy(() -> policy.assertEligible(fixture.campaign(), fixture.stage(), fixture.workOrder()))
                .isInstanceOfSatisfying(RestException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.getErrorCode()).isEqualTo(
                            RepairCampaignWorkOrderEligibilityCode.REPAIR_CAMPAIGN_WORK_ORDER_TYPE_NOT_ALLOWED.name());
                });
    }

    @Test
    void rejectsNormalDepartmentMismatch() {
        Fixture fixture = fixture();
        fixture.workOrder().setDepartmentId(UUID.randomUUID());

        assertCode(fixture, RepairCampaignWorkOrderEligibilityCode.REPAIR_CAMPAIGN_WORK_ORDER_DEPARTMENT_NOT_ALLOWED);
    }

    @Test
    void acceptsCrossDepartmentParticipantAndRejectsOutsider() {
        Fixture fixture = fixture();
        UUID participant = UUID.randomUUID();
        fixture.campaign().setScopeType(RepairCampaignScopeType.CROSS_DEPARTMENT);
        fixture.workOrder().setDepartmentId(participant);
        when(campaignDepartments.existsByCampaignIdAndDepartmentIdAndIsDeletedFalse(
                fixture.campaign().getId(), participant)).thenReturn(true);

        assertThat(policy.evaluate(fixture.campaign(), fixture.stage(), fixture.workOrder()).eligible()).isTrue();

        UUID outsider = UUID.randomUUID();
        fixture.workOrder().setDepartmentId(outsider);
        when(campaignDepartments.existsByCampaignIdAndDepartmentIdAndIsDeletedFalse(
                fixture.campaign().getId(), outsider)).thenReturn(false);
        assertCode(fixture, RepairCampaignWorkOrderEligibilityCode.REPAIR_CAMPAIGN_WORK_ORDER_DEPARTMENT_NOT_ALLOWED);
    }

    @Test
    void rejectsPlannedStartOrEndOutsideStageAndAcceptsNullDates() {
        Fixture fixture = fixture();
        fixture.workOrder().setStartPlannedAt(Instant.parse("2025-12-31T18:59:59Z"));
        assertCode(fixture, RepairCampaignWorkOrderEligibilityCode.REPAIR_CAMPAIGN_WORK_ORDER_DATES_OUTSIDE_STAGE);

        fixture.workOrder().setStartPlannedAt(Instant.parse("2026-01-01T00:00:00Z"));
        fixture.workOrder().setEndPlannedAt(Instant.parse("2026-02-01T00:00:00Z"));
        assertCode(fixture, RepairCampaignWorkOrderEligibilityCode.REPAIR_CAMPAIGN_WORK_ORDER_DATES_OUTSIDE_STAGE);

        fixture.workOrder().setStartPlannedAt(null);
        fixture.workOrder().setEndPlannedAt(null);
        assertThat(policy.evaluate(fixture.campaign(), fixture.stage(), fixture.workOrder()).eligible()).isTrue();
    }

    @Test
    void acceptsNoBudgetLineButRequiresCampaignBudgetForExistingLine() {
        Fixture fixture = fixture();
        assertThat(policy.evaluate(fixture.campaign(), fixture.stage(), fixture.workOrder()).eligible()).isTrue();

        fixture.workOrder().setBudgetLineId(UUID.randomUUID());
        fixture.campaign().setMaintenanceBudgetId(null);
        assertCode(fixture, RepairCampaignWorkOrderEligibilityCode.REPAIR_CAMPAIGN_MAINTENANCE_BUDGET_REQUIRED);
    }

    @Test
    void rejectsMissingOrForeignBudgetLine() {
        Fixture fixture = fixture();
        UUID lineId = UUID.randomUUID();
        fixture.workOrder().setBudgetLineId(lineId);
        fixture.campaign().setMaintenanceBudgetId(UUID.randomUUID());
        when(budgetLines.findByIdAndIsDeletedFalse(lineId)).thenReturn(Optional.empty());
        assertCode(fixture, RepairCampaignWorkOrderEligibilityCode.REPAIR_CAMPAIGN_WORK_ORDER_BUDGET_LINE_NOT_FOUND);

        BudgetLine line = new BudgetLine();
        MaintenanceBudget foreignBudget = new MaintenanceBudget();
        foreignBudget.setId(UUID.randomUUID());
        line.setBudget(foreignBudget);
        when(budgetLines.findByIdAndIsDeletedFalse(lineId)).thenReturn(Optional.of(line));
        assertCode(fixture, RepairCampaignWorkOrderEligibilityCode.REPAIR_CAMPAIGN_WORK_ORDER_BUDGET_NOT_ALLOWED);
    }

    @Test
    void rejectsForeignStageAndClosedOrCancelledCampaign() {
        Fixture fixture = fixture();
        RepairCampaign anotherCampaign = new RepairCampaign();
        anotherCampaign.setId(UUID.randomUUID());
        fixture.stage().setCampaign(anotherCampaign);
        assertCode(fixture, RepairCampaignWorkOrderEligibilityCode.REPAIR_CAMPAIGN_STAGE_MISMATCH);

        fixture.stage().setCampaign(fixture.campaign());
        fixture.campaign().setStatus(RepairCampaignStatus.CLOSED);
        assertCode(fixture, RepairCampaignWorkOrderEligibilityCode.REPAIR_CAMPAIGN_NOT_ATTACHABLE);

        fixture.campaign().setStatus(RepairCampaignStatus.CANCELLED);
        assertCode(fixture, RepairCampaignWorkOrderEligibilityCode.REPAIR_CAMPAIGN_NOT_ATTACHABLE);
    }

    @Test
    void preservesEquipmentStatusExistingLinkCompletedAndClosingNonRules() {
        Fixture fixture = fixture();
        fixture.workOrder().setEquipmentId(UUID.randomUUID());
        fixture.workOrder().setStatus(WorkOrderStatus.CLOSED);
        fixture.workOrder().setRepairCampaignId(UUID.randomUUID());
        fixture.workOrder().setRepairCampaignStageId(UUID.randomUUID());
        fixture.campaign().setStatus(RepairCampaignStatus.COMPLETED);
        assertThat(policy.evaluate(fixture.campaign(), fixture.stage(), fixture.workOrder()).eligible()).isTrue();

        fixture.campaign().setStatus(RepairCampaignStatus.CLOSING);
        assertThat(policy.evaluate(fixture.campaign(), fixture.stage(), fixture.workOrder()).eligible()).isTrue();
    }

    private void assertCode(Fixture fixture, RepairCampaignWorkOrderEligibilityCode code) {
        assertThat(policy.evaluate(fixture.campaign(), fixture.stage(), fixture.workOrder()))
                .extracting(RepairCampaignWorkOrderEligibilityResult::eligible,
                        RepairCampaignWorkOrderEligibilityResult::reasonCode)
                .containsExactly(false, code);
    }

    private Fixture fixture() {
        UUID campaignId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        RepairCampaign campaign = new RepairCampaign();
        campaign.setId(campaignId);
        campaign.setDepartmentId(departmentId);
        campaign.setScopeType(RepairCampaignScopeType.DEPARTMENT);
        campaign.setStatus(RepairCampaignStatus.DRAFT);

        RepairCampaignStage stage = new RepairCampaignStage();
        stage.setId(UUID.randomUUID());
        stage.setCampaign(campaign);
        stage.setStartDate(LocalDate.of(2026, 1, 1));
        stage.setEndDate(LocalDate.of(2026, 1, 31));

        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(UUID.randomUUID());
        workOrder.setDepartmentId(departmentId);
        workOrder.setEquipmentId(UUID.randomUUID());
        workOrder.setType(WorkOrderType.OVERHAUL);
        workOrder.setStatus(WorkOrderStatus.DRAFT);
        workOrder.setStartPlannedAt(Instant.parse("2026-01-01T00:00:00Z"));
        workOrder.setEndPlannedAt(Instant.parse("2026-01-30T00:00:00Z"));
        return new Fixture(campaign, stage, workOrder);
    }

    private record Fixture(RepairCampaign campaign, RepairCampaignStage stage, WorkOrder workOrder) { }
}
