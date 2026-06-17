package com.toir.service;

import com.toir.dto.approval.CreateApprovalRequest;
import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.entity.projects.ProcurementRequest;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.ApprovalTargetType;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.ProcurementRequestRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.maintenance.MaintenanceBudgetRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.security.ScopeAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApprovalScopeService {

    private final ScopeAccessService scopeAccessService;
    private final PprPlanRepository pprPlanRepository;
    private final PprTaskRepository pprTaskRepository;
    private final RepairRequestRepository repairRequestRepository;
    private final WorkOrderRepository workOrderRepository;
    private final ProcurementRequestRepository procurementRequestRepository;
    private final MaintenanceBudgetRepository maintenanceBudgetRepository;
    private final ActualCostRepository actualCostRepository;
    private final FinanceScopeService financeScopeService;

    public boolean canReadApproval(ApprovalRequest approval) {
        if (approval == null) {
            return false;
        }
        if (scopeAccessService.isScopeAdmin()) {
            return true;
        }
        return isRequester(approval)
                || isCurrentPendingApprover(approval)
                || canAccessLinkedDocumentScope(effectiveTargetType(approval), effectiveTargetId(approval));
    }

    public void assertCanReadApproval(ApprovalRequest approval) {
        if (!canReadApproval(approval)) {
            throw forbidden();
        }
    }

    public void assertCanCreateApproval(CreateApprovalRequest request) {
        if (scopeAccessService.isScopeAdmin()) {
            return;
        }
        if (request == null || !matchesCurrentPrincipal(request.requesterId())) {
            throw forbidden();
        }
        ApprovalTargetType targetType = request.targetType() == null
                ? ApprovalTargetType.fromDocumentType(request.documentType())
                : request.targetType();
        UUID targetId = request.targetId() == null ? request.documentId() : request.targetId();
        if (hasLinkedDocumentScopeResolver(targetType)
                && !canAccessLinkedDocumentScope(targetType, targetId)) {
            throw forbidden();
        }
    }

    public void assertCanDecideApproval(ApprovalRequest approval, ApprovalStep currentStep) {
        if (scopeAccessService.isScopeAdmin()) {
            return;
        }
        if (approval == null
                || approval.getStatus() != ApprovalStatus.PENDING
                || currentStep == null
                || currentStep.getDecision() != ApprovalDecision.PENDING
                || currentStep.getStepNumber() != approval.getCurrentStep()
                || !matchesCurrentPrincipal(currentStep.getApproverId())) {
            throw forbidden();
        }
    }

    public void assertCanCancelApproval(ApprovalRequest approval) {
        if (approval == null) {
            throw forbidden();
        }
        if (scopeAccessService.isScopeAdmin()
                || isRequester(approval)
                || canAccessLinkedDocumentScope(effectiveTargetType(approval), effectiveTargetId(approval))) {
            return;
        }
        throw forbidden();
    }

    public Optional<UUID> resolveApprovalDocumentDepartment(ApprovalRequest approval) {
        if (approval == null) {
            return Optional.empty();
        }
        return resolveApprovalDocumentDepartment(effectiveTargetType(approval), effectiveTargetId(approval));
    }

    private boolean isRequester(ApprovalRequest approval) {
        return matchesCurrentPrincipal(approval.getRequesterId());
    }

    private boolean isCurrentPendingApprover(ApprovalRequest approval) {
        if (approval.getStatus() != ApprovalStatus.PENDING) {
            return false;
        }
        return approval.getSteps().stream()
                .filter(step -> step.getStepNumber() == approval.getCurrentStep())
                .filter(step -> step.getDecision() == ApprovalDecision.PENDING)
                .findFirst()
                .map(ApprovalStep::getApproverId)
                .map(this::matchesCurrentPrincipal)
                .orElse(false);
    }

    private boolean matchesCurrentPrincipal(UUID candidateId) {
        if (candidateId == null) {
            return false;
        }
        return currentPrincipalIds().contains(candidateId);
    }

    private Set<UUID> currentPrincipalIds() {
        Set<UUID> ids = new LinkedHashSet<>();
        UUID userId = scopeAccessService.currentUserIdOrNull();
        if (userId != null) {
            ids.add(userId);
        }
        scopeAccessService.currentEmployeeId().ifPresent(ids::add);
        return ids;
    }

    private boolean canAccessLinkedDocumentScope(ApprovalTargetType targetType, UUID targetId) {
        if (targetId == null) {
            return false;
        }
        if (targetType == ApprovalTargetType.ACTUAL_COST) {
            return actualCostRepository.findByIdAndIsDeletedFalse(targetId)
                    .map(this::canReadActualCost)
                    .orElse(false);
        }
        if (isProcurementDocument(targetType)) {
            return procurementRequestRepository.findByIdAndIsDeletedFalse(targetId)
                    .map(this::canAccessProcurementRequest)
                    .orElse(false);
        }
        return resolveApprovalDocumentDepartment(targetType, targetId)
                .map(scopeAccessService::canAccessDepartment)
                .orElse(false);
    }

    private boolean canReadActualCost(com.toir.entity.projects.ActualCost actualCost) {
        try {
            financeScopeService.assertCanReadActualCost(actualCost);
            return true;
        } catch (AccessDeniedException ex) {
            return false;
        }
    }

    private Optional<UUID> resolveApprovalDocumentDepartment(ApprovalTargetType targetType, UUID targetId) {
        if (targetType == null || targetId == null) {
            return Optional.empty();
        }
        return switch (targetType) {
            case PPR_PLAN -> pprPlanRepository.findByIdAndIsDeletedFalse(targetId)
                    .map(PprPlan::getDepartmentId);
            case PPR_TASK -> pprTaskRepository.findByIdAndIsDeletedFalseWithPlan(targetId)
                    .map(PprTask::getPlan)
                    .map(PprPlan::getDepartmentId);
            case REPAIR_REQUEST -> repairRequestRepository.findByIdAndIsDeletedFalse(targetId)
                    .map(RepairRequest::getDepartmentId);
            case WORK_ORDER -> workOrderRepository.findByIdAndIsDeletedFalse(targetId)
                    .map(WorkOrder::getDepartmentId);
            case PROCUREMENT, PROCUREMENT_REQUEST -> procurementRequestRepository.findByIdAndIsDeletedFalse(targetId)
                    .map(ProcurementRequest::getDepartmentId);
            case BUDGET, MAINTENANCE_BUDGET -> maintenanceBudgetRepository.findByIdAndIsDeletedFalse(targetId)
                    .map(MaintenanceBudget::getDepartmentId);
            default -> Optional.empty();
        };
    }

    private boolean canAccessProcurementRequest(ProcurementRequest request) {
        if (request.getDepartmentId() != null && scopeAccessService.canAccessDepartment(request.getDepartmentId())) {
            return true;
        }
        return request.getWarehouseId() != null && scopeAccessService.canAccessWarehouse(request.getWarehouseId());
    }

    private boolean isProcurementDocument(ApprovalTargetType targetType) {
        if (targetType == null) {
            return false;
        }
        return switch (targetType) {
            case PROCUREMENT, PROCUREMENT_REQUEST -> true;
            default -> false;
        };
    }

    private boolean hasLinkedDocumentScopeResolver(ApprovalTargetType targetType) {
        if (targetType == null) {
            return false;
        }
        return switch (targetType) {
            case PPR_PLAN, PPR_TASK, REPAIR_REQUEST, WORK_ORDER,
                 PROCUREMENT, PROCUREMENT_REQUEST, BUDGET, MAINTENANCE_BUDGET, ACTUAL_COST -> true;
            default -> false;
        };
    }

    private ApprovalTargetType effectiveTargetType(ApprovalRequest approval) {
        if (approval.getTargetType() != null) {
            return approval.getTargetType();
        }
        return ApprovalTargetType.fromDocumentType(approval.getDocumentType());
    }

    private UUID effectiveTargetId(ApprovalRequest approval) {
        return approval.getTargetId() == null ? approval.getDocumentId() : approval.getTargetId();
    }

    private AccessDeniedException forbidden() {
        return new AccessDeniedException("Access denied by approval scope");
    }
}
