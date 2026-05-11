package com.toir.mapper;

import com.toir.dto.actualcostrouteoverride.*;
import com.toir.entity.Department;
import com.toir.entity.contractors.ContractorWork;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.ActualCostReviewRouteOverride;
import com.toir.entity.projects.CostCategory;
import com.toir.entity.projects.FinancialApprovalRule;
import com.toir.entity.users.User;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.projects.FinancialApprovalRuleRepository;
import com.toir.repository.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ActualCostReviewRouteOverrideResponseMapper {

    private final WorkOrderRepository workOrderRepository;
    private final ContractorWorkRepository contractorWorkRepository;
    private final DepartmentRepository departmentRepository;
    private final CostCategoryRepository costCategoryRepository;
    private final FinancialApprovalRuleRepository financialApprovalRuleRepository;
    private final UserRepository userRepository;

    public ActualCostReviewRouteOverrideResponseDto toResponse(
            ActualCostReviewRouteOverride override,
            ActualCost actualCost
    ) {
        WorkOrder workOrder = actualCost != null ? resolveWorkOrder(actualCost).orElse(null) : null;
        Department department = resolveDepartment(override, workOrder).orElse(null);

        return new ActualCostReviewRouteOverrideResponseDto(
                override.getId(),
                toActualCostView(actualCost, override, workOrder, department),
                override.getDepartmentId(),
                override.getApprovalRoleCode(),
                override.getEscalationRoleCode(),
                override.getThresholdHours(),
                override.getComment(),
                override.isActive(),
                override.getCreatedById(),
                toUserShort(override.getCreatedById()),
                toDepartmentShort(department),
                toUserShort(override.getDeactivatedById()),
                override.getDeactivationComment(),
                override.getDeactivatedAt()
        );
    }

    private ActualCostRouteViewDto toActualCostView(
            ActualCost actualCost,
            ActualCostReviewRouteOverride override,
            WorkOrder workOrder,
            Department department
    ) {
        if (actualCost == null) {
            return new ActualCostRouteViewDto(
                    override.getActualCostId(),
                    null,
                    "OVERRIDE",
                    false,
                    null,
                    null,
                    null,
                    override.getApprovalRoleCode(),
                    override.getEscalationRoleCode(),
                    null,
                    null,
                    emptyCostCategory(),
                    emptyApprovalRule(),
                    null,
                    toDepartmentShort(department),
                    toWorkOrderShort(workOrder),
                    emptyContractorWork()
            );
        }

        CostCategory costCategory = actualCost.getCostCategoryId() != null
                ? costCategoryRepository.findByIdAndIsDeletedFalse(actualCost.getCostCategoryId()).orElse(null)
                : null;

        FinancialApprovalRule approvalRule = null;

        ContractorWork contractorWork = null;
        if (actualCost.getContractorWorkId() != null) {
            contractorWork = contractorWorkRepository
                    .findByIdAndIsDeletedFalse(actualCost.getContractorWorkId())
                    .orElse(null);
        }

        WorkOrder contractorWorkOrder = null;
        if (contractorWork != null && contractorWork.getWorkOrderId() != null) {
            contractorWorkOrder = workOrderRepository
                    .findByIdAndIsDeletedFalse(contractorWork.getWorkOrderId())
                    .orElse(null);
        }

        return new ActualCostRouteViewDto(
                actualCost.getId(),
                actualCost.getStatus(),
                "OVERRIDE",
                calculateIsOverdue(actualCost, override),
                calculateHoursToOverdue(actualCost, override),
                calculateAgeHours(actualCost),
                actualCost.getAmount(),
                override.getApprovalRoleCode(),
                override.getEscalationRoleCode(),
                actualCost.getReviewComment(),
                actualCost.getNotes(),
                toCostCategoryShort(costCategory),
                toApprovalRuleShort(approvalRule),
                null,
                toDepartmentShort(department),
                toWorkOrderShort(workOrder),
                toContractorWorkShort(contractorWork, contractorWorkOrder)
        );
    }

    private Optional<WorkOrder> resolveWorkOrder(ActualCost actualCost) {
        if (actualCost.getWorkOrderId() != null) {
            return workOrderRepository.findByIdAndIsDeletedFalse(actualCost.getWorkOrderId());
        }

        if (actualCost.getContractorWorkId() != null) {
            return contractorWorkRepository.findByIdAndIsDeletedFalse(actualCost.getContractorWorkId())
                    .flatMap(contractorWork -> {
                        if (contractorWork.getWorkOrderId() == null) {
                            return Optional.empty();
                        }
                        return workOrderRepository.findByIdAndIsDeletedFalse(contractorWork.getWorkOrderId());
                    });
        }

        return Optional.empty();
    }

    private Optional<Department> resolveDepartment(
            ActualCostReviewRouteOverride override,
            WorkOrder workOrder
    ) {
        if (override.getDepartmentId() != null) {
            return departmentRepository.findByIdAndIsDeletedFalse(override.getDepartmentId());
        }

        if (workOrder != null && workOrder.getDepartmentId() != null) {
            return departmentRepository.findByIdAndIsDeletedFalse(workOrder.getDepartmentId());
        }

        return Optional.empty();
    }

    private Optional<FinancialApprovalRule> resolveApprovalRule(
            ActualCost actualCost,
            Department department
    ) {
        if (actualCost == null) {
            return Optional.empty();
        }
        UUID departmentId = department != null ? department.getId() : null;

        return financialApprovalRuleRepository.findFirstMatchingRule(
                departmentId,
                actualCost.getAmount()
        );
    }

    private UserShortDto toUserShort(UUID userId) {
        if (userId == null) {
            return new UserShortDto(null, null);
        }

        return userRepository.findByIdAndIsDeletedFalse(userId)
                .map(u -> new UserShortDto(u.getId(), u.getFullName()))
                .orElse(new UserShortDto(userId, null));
    }

    private DepartmentShortDto toDepartmentShort(Department department) {
        if (department == null) {
            return new DepartmentShortDto(null, null, null);
        }

        return new DepartmentShortDto(
                department.getId(),
                department.getCode(),
                department.getName()
        );
    }

    private CostCategoryShortDto toCostCategoryShort(CostCategory costCategory) {
        if (costCategory == null) {
            return emptyCostCategory();
        }

        return new CostCategoryShortDto(costCategory.getId(), costCategory.getCode());
    }

    private ApprovalRuleShortDto toApprovalRuleShort(FinancialApprovalRule approvalRule) {
        if (approvalRule == null) {
            return emptyApprovalRule();
        }

        return new ApprovalRuleShortDto(approvalRule.getId(), approvalRule.getCode());
    }

    private WorkOrderShortDto toWorkOrderShort(WorkOrder workOrder) {
        if (workOrder == null) {
            return emptyWorkOrder();
        }

        return new WorkOrderShortDto(
                workOrder.getId(),
                workOrder.getNumber(),
                workOrder.getTitle()
        );
    }

    private ContractorWorkShortDto toContractorWorkShort(
            ContractorWork contractorWork,
            WorkOrder contractorWorkOrder
    ) {
        if (contractorWork == null) {
            return emptyContractorWork();
        }

        return new ContractorWorkShortDto(
                contractorWork.getId(),
                contractorWork.getDescription(),
                new ContractorShortDto(contractorWork.getContractorId(), null),
                toWorkOrderShort(contractorWorkOrder)
        );
    }

    private Integer calculateAgeHours(ActualCost actualCost) {
        if (actualCost.getCreatedAt() == null) {
            return null;
        }

        return Math.toIntExact(Duration.between(
                actualCost.getCreatedAt(),
                Instant.now()
        ).toHours());
    }

    private Boolean calculateIsOverdue(
            ActualCost actualCost,
            ActualCostReviewRouteOverride override
    ) {
        Integer ageHours = calculateAgeHours(actualCost);

        if (ageHours == null) {
            return false;
        }

        int thresholdHours = safeThreshold(override);
        return ageHours >= thresholdHours;
    }

    private Integer calculateHoursToOverdue(
            ActualCost actualCost,
            ActualCostReviewRouteOverride override
    ) {
        Integer ageHours = calculateAgeHours(actualCost);

        if (ageHours == null) {
            return null;
        }

        int thresholdHours = safeThreshold(override);
        return Math.max(thresholdHours - ageHours, 0);
    }

    private int safeThreshold(ActualCostReviewRouteOverride override) {
        return Math.max(override.getThresholdHours(), 0);
    }

    private CostCategoryShortDto emptyCostCategory() {
        return new CostCategoryShortDto(null, null);
    }

    private ApprovalRuleShortDto emptyApprovalRule() {
        return new ApprovalRuleShortDto(null, null);
    }

    private WorkOrderShortDto emptyWorkOrder() {
        return new WorkOrderShortDto(null, null, null);
    }

    private ContractorWorkShortDto emptyContractorWork() {
        return new ContractorWorkShortDto(
                null,
                null,
                new ContractorShortDto(null, null),
                emptyWorkOrder()
        );
    }
}
