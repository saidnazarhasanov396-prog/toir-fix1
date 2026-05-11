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
        WorkOrder workOrder = resolveWorkOrder(actualCost).orElse(null);
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
        CostCategory costCategory = costCategoryRepository
                .findById(actualCost.getCostCategoryId())
                .orElse(null);

        FinancialApprovalRule approvalRule = resolveApprovalRule(actualCost, department).orElse(null);

        ContractorWork contractorWork = null;
        if (actualCost.getContractorWorkId() != null) {
            contractorWork = contractorWorkRepository
                    .findById(actualCost.getContractorWorkId())
                    .orElse(null);
        }

        WorkOrder contractorWorkOrder = null;
        if (contractorWork != null && contractorWork.getWorkOrderId() != null) {
            contractorWorkOrder = workOrderRepository
                    .findById(contractorWork.getWorkOrderId())
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
            return workOrderRepository.findById(actualCost.getWorkOrderId());
        }

        if (actualCost.getContractorWorkId() != null) {
            return contractorWorkRepository.findById(actualCost.getContractorWorkId())
                    .flatMap(contractorWork -> {
                        if (contractorWork.getWorkOrderId() == null) {
                            return Optional.empty();
                        }
                        return workOrderRepository.findById(contractorWork.getWorkOrderId());
                    });
        }

        return Optional.empty();
    }

    private Optional<Department> resolveDepartment(
            ActualCostReviewRouteOverride override,
            WorkOrder workOrder
    ) {
        if (override.getDepartmentId() != null) {
            return departmentRepository.findById(override.getDepartmentId());
        }

        if (workOrder != null && workOrder.getDepartmentId() != null) {
            return departmentRepository.findById(workOrder.getDepartmentId());
        }

        return Optional.empty();
    }

    private Optional<FinancialApprovalRule> resolveApprovalRule(
            ActualCost actualCost,
            Department department
    ) {
        UUID departmentId = department != null ? department.getId() : null;

        return financialApprovalRuleRepository.findFirstMatchingRule(
                departmentId,
                actualCost.getAmount()
        );
    }

    private UserShortDto toUserShort(UUID userId) {
        if (userId == null) {
            return null;
        }

        return userRepository.findById(userId)
                .map(User::getFullName)
                .map(UserShortDto::new)
                .orElse(null);
    }

    private DepartmentShortDto toDepartmentShort(Department department) {
        if (department == null) {
            return null;
        }

        return new DepartmentShortDto(
                department.getCode(),
                department.getName()
        );
    }

    private CostCategoryShortDto toCostCategoryShort(CostCategory costCategory) {
        if (costCategory == null) {
            return null;
        }

        return new CostCategoryShortDto(costCategory.getCode());
    }

    private ApprovalRuleShortDto toApprovalRuleShort(FinancialApprovalRule approvalRule) {
        if (approvalRule == null) {
            return null;
        }

        return new ApprovalRuleShortDto(approvalRule.getCode());
    }

    private WorkOrderShortDto toWorkOrderShort(WorkOrder workOrder) {
        if (workOrder == null) {
            return null;
        }

        return new WorkOrderShortDto(
                workOrder.getNumber(),
                workOrder.getTitle()
        );
    }

    private ContractorWorkShortDto toContractorWorkShort(
            ContractorWork contractorWork,
            WorkOrder contractorWorkOrder
    ) {
        if (contractorWork == null) {
            return null;
        }

        return new ContractorWorkShortDto(
                contractorWork.getDescription(),
                null,
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

        return ageHours >= override.getThresholdHours();
    }

    private Integer calculateHoursToOverdue(
            ActualCost actualCost,
            ActualCostReviewRouteOverride override
    ) {
        Integer ageHours = calculateAgeHours(actualCost);

        if (ageHours == null) {
            return null;
        }

        return Math.max(override.getThresholdHours() - ageHours, 0);
    }
}