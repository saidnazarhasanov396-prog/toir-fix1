package com.toir.service.repair;

import com.toir.dto.repaircampaign.RepairCampaignWorkOrderEligibilityResult;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.BudgetLine;
import com.toir.entity.repair.RepairCampaign;
import com.toir.entity.repair.RepairCampaignStage;
import com.toir.enums.RepairCampaignScopeType;
import com.toir.enums.RepairCampaignStatus;
import com.toir.enums.RepairCampaignWorkOrderEligibilityCode;
import com.toir.enums.WorkOrderType;
import com.toir.exception.RestException;
import com.toir.repository.projects.BudgetLineRepository;
import com.toir.repository.repair.RepairCampaignDepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static com.toir.dto.repaircampaign.RepairCampaignWorkOrderEligibilityResult.ineligible;

@Component
@RequiredArgsConstructor
public class RepairCampaignWorkOrderEligibilityPolicy {

    private static final ZoneId CAMPAIGN_DATE_ZONE = ZoneId.of("Asia/Tashkent");
    private static final Set<WorkOrderType> ALLOWED_TYPES = EnumSet.of(
            WorkOrderType.OVERHAUL,
            WorkOrderType.MEDIUM_REPAIR,
            WorkOrderType.CAPITAL_REPAIR
    );

    private final RepairCampaignDepartmentRepository campaignDepartmentRepository;
    private final BudgetLineRepository budgetLineRepository;

    public RepairCampaignWorkOrderEligibilityResult evaluate(
            RepairCampaign campaign,
            RepairCampaignStage stage,
            WorkOrder workOrder
    ) {
        if (stage.getCampaign() == null || !campaign.getId().equals(stage.getCampaign().getId())) {
            return ineligible(RepairCampaignWorkOrderEligibilityCode.REPAIR_CAMPAIGN_STAGE_MISMATCH);
        }
        if (campaign.getStatus() == RepairCampaignStatus.CLOSED
                || campaign.getStatus() == RepairCampaignStatus.CANCELLED) {
            return ineligible(RepairCampaignWorkOrderEligibilityCode.REPAIR_CAMPAIGN_NOT_ATTACHABLE);
        }
        if (!ALLOWED_TYPES.contains(workOrder.getType())) {
            return ineligible(RepairCampaignWorkOrderEligibilityCode.REPAIR_CAMPAIGN_WORK_ORDER_TYPE_NOT_ALLOWED);
        }
        if (!departmentAllowed(campaign, workOrder.getDepartmentId())) {
            return ineligible(RepairCampaignWorkOrderEligibilityCode.REPAIR_CAMPAIGN_WORK_ORDER_DEPARTMENT_NOT_ALLOWED);
        }
        if (!plannedDatesAllowed(stage, workOrder)) {
            return ineligible(RepairCampaignWorkOrderEligibilityCode.REPAIR_CAMPAIGN_WORK_ORDER_DATES_OUTSIDE_STAGE);
        }
        if (workOrder.getBudgetLineId() == null) {
            return RepairCampaignWorkOrderEligibilityResult.allowed();
        }
        if (campaign.getMaintenanceBudgetId() == null) {
            return ineligible(RepairCampaignWorkOrderEligibilityCode.REPAIR_CAMPAIGN_MAINTENANCE_BUDGET_REQUIRED);
        }
        BudgetLine budgetLine = budgetLineRepository.findByIdAndIsDeletedFalse(workOrder.getBudgetLineId())
                .orElse(null);
        if (budgetLine == null) {
            return ineligible(RepairCampaignWorkOrderEligibilityCode.REPAIR_CAMPAIGN_WORK_ORDER_BUDGET_LINE_NOT_FOUND);
        }
        if (budgetLine.getBudget() == null
                || !campaign.getMaintenanceBudgetId().equals(budgetLine.getBudget().getId())) {
            return ineligible(RepairCampaignWorkOrderEligibilityCode.REPAIR_CAMPAIGN_WORK_ORDER_BUDGET_NOT_ALLOWED);
        }
        return RepairCampaignWorkOrderEligibilityResult.allowed();
    }

    public void assertEligible(
            RepairCampaign campaign,
            RepairCampaignStage stage,
            WorkOrder workOrder
    ) {
        RepairCampaignWorkOrderEligibilityResult result = evaluate(campaign, stage, workOrder);
        if (result.eligible()) {
            return;
        }
        throw new RestException(
                message(result.reasonCode(), campaign, workOrder.getBudgetLineId()),
                status(result.reasonCode()),
                result.reasonCode().name()
        );
    }

    public void assertStageBelongsToCampaign(RepairCampaign campaign, RepairCampaignStage stage) {
        if (stage.getCampaign() == null || !campaign.getId().equals(stage.getCampaign().getId())) {
            RepairCampaignWorkOrderEligibilityCode code =
                    RepairCampaignWorkOrderEligibilityCode.REPAIR_CAMPAIGN_STAGE_MISMATCH;
            throw new RestException(message(code, campaign, null), status(code), code.name());
        }
    }

    private boolean departmentAllowed(RepairCampaign campaign, UUID workOrderDepartmentId) {
        if (workOrderDepartmentId == null) {
            return true;
        }
        if (campaign.getScopeType() == RepairCampaignScopeType.CROSS_DEPARTMENT) {
            return workOrderDepartmentId.equals(campaign.getDepartmentId())
                    || campaignDepartmentRepository.existsByCampaignIdAndDepartmentIdAndIsDeletedFalse(
                    campaign.getId(), workOrderDepartmentId);
        }
        return campaign.getDepartmentId() == null
                || campaign.getDepartmentId().equals(workOrderDepartmentId);
    }

    private boolean plannedDatesAllowed(RepairCampaignStage stage, WorkOrder workOrder) {
        return plannedDateAllowed(stage, workOrder.getStartPlannedAt() == null
                ? null
                : workOrder.getStartPlannedAt().atZone(CAMPAIGN_DATE_ZONE).toLocalDate())
                && plannedDateAllowed(stage, workOrder.getEndPlannedAt() == null
                ? null
                : workOrder.getEndPlannedAt().atZone(CAMPAIGN_DATE_ZONE).toLocalDate());
    }

    private boolean plannedDateAllowed(RepairCampaignStage stage, LocalDate plannedDate) {
        if (plannedDate == null || stage.getStartDate() == null || stage.getEndDate() == null) {
            return true;
        }
        return !plannedDate.isBefore(stage.getStartDate()) && !plannedDate.isAfter(stage.getEndDate());
    }

    private HttpStatus status(RepairCampaignWorkOrderEligibilityCode code) {
        return code == RepairCampaignWorkOrderEligibilityCode.REPAIR_CAMPAIGN_WORK_ORDER_BUDGET_LINE_NOT_FOUND
                ? HttpStatus.NOT_FOUND
                : HttpStatus.BAD_REQUEST;
    }

    private String message(
            RepairCampaignWorkOrderEligibilityCode code,
            RepairCampaign campaign,
            UUID budgetLineId
    ) {
        return switch (code) {
            case REPAIR_CAMPAIGN_STAGE_MISMATCH -> "Stage does not belong to repair campaign";
            case REPAIR_CAMPAIGN_NOT_ATTACHABLE -> "Cannot link work orders to closed/cancelled campaign";
            case REPAIR_CAMPAIGN_WORK_ORDER_TYPE_NOT_ALLOWED ->
                    "Campaign work order type must be OVERHAUL, MEDIUM_REPAIR, or CAPITAL_REPAIR";
            case REPAIR_CAMPAIGN_WORK_ORDER_DEPARTMENT_NOT_ALLOWED ->
                    campaign.getScopeType() == RepairCampaignScopeType.CROSS_DEPARTMENT
                            ? "Work order department is not a campaign participant"
                            : "Work order department must match repair campaign department";
            case REPAIR_CAMPAIGN_WORK_ORDER_DATES_OUTSIDE_STAGE ->
                    "Work order planned dates must fit repair campaign stage dates";
            case REPAIR_CAMPAIGN_MAINTENANCE_BUDGET_REQUIRED ->
                    "Campaign must be linked to a maintenance budget before assigning work order budget lines";
            case REPAIR_CAMPAIGN_WORK_ORDER_BUDGET_NOT_ALLOWED ->
                    "Work order budget line must belong to the repair campaign maintenance budget";
            case REPAIR_CAMPAIGN_WORK_ORDER_BUDGET_LINE_NOT_FOUND -> "Budget line not found: " + budgetLineId;
            case ELIGIBLE -> throw new IllegalArgumentException("Eligible result has no error message");
        };
    }
}
