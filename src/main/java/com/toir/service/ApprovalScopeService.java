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
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Locale;
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
                || canAccessLinkedDocumentScope(approval.getDocumentType(), approval.getDocumentId());
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
        if (hasLinkedDocumentScopeResolver(request.documentType())
                && !canAccessLinkedDocumentScope(request.documentType(), request.documentId())) {
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
                || canAccessLinkedDocumentScope(approval.getDocumentType(), approval.getDocumentId())) {
            return;
        }
        throw forbidden();
    }

    public Optional<UUID> resolveApprovalDocumentDepartment(ApprovalRequest approval) {
        if (approval == null) {
            return Optional.empty();
        }
        return resolveApprovalDocumentDepartment(approval.getDocumentType(), approval.getDocumentId());
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

    private boolean canAccessLinkedDocumentScope(String documentType, UUID documentId) {
        if (documentId == null) {
            return false;
        }
        String type = normalizeDocumentType(documentType);
        if ("ACTUAL_COST".equals(type)) {
            return actualCostRepository.findByIdAndIsDeletedFalse(documentId)
                    .map(this::canReadActualCost)
                    .orElse(false);
        }
        if (isProcurementDocument(documentType)) {
            return procurementRequestRepository.findByIdAndIsDeletedFalse(documentId)
                    .map(this::canAccessProcurementRequest)
                    .orElse(false);
        }
        return resolveApprovalDocumentDepartment(documentType, documentId)
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

    private Optional<UUID> resolveApprovalDocumentDepartment(String documentType, UUID documentId) {
        if (documentId == null) {
            return Optional.empty();
        }
        return switch (normalizeDocumentType(documentType)) {
            case "PPR", "PPR_PLAN" -> pprPlanRepository.findByIdAndIsDeletedFalse(documentId)
                    .map(PprPlan::getDepartmentId);
            case "PPR_TASK" -> pprTaskRepository.findByIdAndIsDeletedFalseWithPlan(documentId)
                    .map(PprTask::getPlan)
                    .map(PprPlan::getDepartmentId);
            case "REPAIR_REQUEST" -> repairRequestRepository.findByIdAndIsDeletedFalse(documentId)
                    .map(RepairRequest::getDepartmentId);
            case "WORK_ORDER" -> workOrderRepository.findByIdAndIsDeletedFalse(documentId)
                    .map(WorkOrder::getDepartmentId);
            case "PROCUREMENT", "PROCUREMENT_REQUEST" -> procurementRequestRepository.findByIdAndIsDeletedFalse(documentId)
                    .map(ProcurementRequest::getDepartmentId);
            case "BUDGET", "MAINTENANCE_BUDGET" -> maintenanceBudgetRepository.findByIdAndIsDeletedFalse(documentId)
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

    private boolean isProcurementDocument(String documentType) {
        return switch (normalizeDocumentType(documentType)) {
            case "PROCUREMENT", "PROCUREMENT_REQUEST" -> true;
            default -> false;
        };
    }

    private boolean hasLinkedDocumentScopeResolver(String documentType) {
        return switch (normalizeDocumentType(documentType)) {
            case "PPR", "PPR_PLAN", "PPR_TASK", "REPAIR_REQUEST", "WORK_ORDER",
                 "PROCUREMENT", "PROCUREMENT_REQUEST", "BUDGET", "MAINTENANCE_BUDGET", "ACTUAL_COST" -> true;
            default -> false;
        };
    }

    private String normalizeDocumentType(String documentType) {
        if (!StringUtils.hasText(documentType)) {
            return "";
        }
        return documentType.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
    }

    private AccessDeniedException forbidden() {
        return new AccessDeniedException("Access denied by approval scope");
    }
}
