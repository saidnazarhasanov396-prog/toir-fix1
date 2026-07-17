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
import com.toir.entity.users.Role;
import com.toir.entity.users.User;
import com.toir.enums.ApprovalDecision;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.ApprovalTargetType;
import com.toir.enums.UserStatus;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.ProcurementRequestRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.PlannedShutdownRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.maintenance.MaintenanceBudgetRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.repository.repair.RepairCampaignRepository;
import com.toir.repository.equipment.EquipmentCommissioningActRepository;
import com.toir.repository.users.UserRepository;
import com.toir.security.ScopeAccessService;
import com.toir.security.PermissionConstants;
import com.toir.security.SecurityAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

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
    private final UserRepository userRepository;
    private final EquipmentCommissioningActRepository equipmentCommissioningActRepository;
    private final PlannedShutdownRepository plannedShutdownRepository;
    private final RepairCampaignRepository repairCampaignRepository;
    private final SecurityAccessService securityAccessService;

    public boolean canReadApproval(ApprovalRequest approval) {
        if (approval == null) {
            return false;
        }
        if (scopeAccessService.isScopeAdmin()) {
            return true;
        }
        if ((effectiveTargetType(approval) == ApprovalTargetType.PLANNED_SHUTDOWN
                || effectiveTargetType(approval) == ApprovalTargetType.REPAIR_CAMPAIGN)
                && !canAccessLinkedDocumentScope(effectiveTargetType(approval), effectiveTargetId(approval))) {
            return false;
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
        assertCanDecideApproval(approval, currentStep, null);
    }

    public void assertCanDecideApproval(ApprovalRequest approval, ApprovalStep currentStep, UUID delegatedForId) {
        if (approval == null
                || approval.getStatus() != ApprovalStatus.PENDING
                || currentStep == null
                || currentStep.getDecision() != ApprovalDecision.PENDING
                || currentStep.getStepNumber() != approval.getCurrentStep()) {
            throw forbidden();
        }
        if (!scopeAccessService.isScopeAdmin()
                && (effectiveTargetType(approval) == ApprovalTargetType.PLANNED_SHUTDOWN
                || effectiveTargetType(approval) == ApprovalTargetType.REPAIR_CAMPAIGN)
                && !canAccessLinkedDocumentScope(effectiveTargetType(approval), effectiveTargetId(approval))) {
            throw forbidden();
        }
        if (delegatedForId != null) {
            if (!Objects.equals(currentStep.getApproverId(), delegatedForId)) {
                throw forbidden();
            }
            return;
        }
        if (scopeAccessService.isScopeAdmin() && currentStep.getApproverId() != null) {
            return;
        }
        if (!canCurrentPrincipalActOnStep(currentStep)) {
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

    public void assertCanUpdateApproval(ApprovalRequest approval) {
        if (approval != null
                && (scopeAccessService.isScopeAdmin()
                || scopeAccessService.hasAuthority(PermissionConstants.APPROVAL_UPDATE)
                || isRequester(approval))) {
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
                .map(this::canCurrentPrincipalActOnStep)
                .orElse(false);
    }

    private boolean canCurrentPrincipalActOnStep(ApprovalStep step) {
        if (step == null) {
            return false;
        }
        if (step.getApproverId() != null) {
            return matchesCurrentPrincipal(step.getApproverId());
        }
        return currentUserCanActOnApproverStep(step.getApproverRole());
    }

    private boolean currentUserCanActOnApproverStep(String approverRole) {
        if (!StringUtils.hasText(approverRole)) {
            return false;
        }
        String normalizedRole = approverRole.trim();
        if (currentUserHasRole(normalizedRole)) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return securityAccessService.hasPermission(authentication, normalizedRole);
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

    private boolean currentUserHasRole(String roleCode) {
        UUID userId = scopeAccessService.currentUserIdOrNull();
        if (userId == null || !StringUtils.hasText(roleCode)) {
            return false;
        }
        String normalizedRole = roleCode.trim();
        return userRepository.findByIdAndIsDeletedFalse(userId)
                .filter(this::isActiveUser)
                .filter(user -> hasRole(user, normalizedRole))
                .isPresent();
    }

    private boolean isActiveUser(User user) {
        return user != null && (user.getStatus() == null || user.getStatus() == UserStatus.ACTIVE);
    }

    private boolean hasRole(User user, String roleCode) {
        return roleStream(user)
                .map(Role::getCode)
                .filter(Objects::nonNull)
                .anyMatch(roleCode::equals);
    }

    private Stream<Role> roleStream(User user) {
        if (user == null) {
            return Stream.empty();
        }
        Stream<Role> primary = user.getPrimaryRole() == null ? Stream.empty() : Stream.of(user.getPrimaryRole());
        Stream<Role> additional = user.getRoles() == null ? Stream.empty() : user.getRoles().stream();
        return Stream.concat(primary, additional).filter(Objects::nonNull);
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
            case EQUIPMENT_COMMISSIONING -> equipmentCommissioningActRepository == null
                    ? Optional.empty()
                    : equipmentCommissioningActRepository.findByIdAndIsDeletedFalse(targetId)
                            .map(com.toir.entity.equipment.EquipmentCommissioningAct::getTargetDepartmentId);
            case PLANNED_SHUTDOWN -> plannedShutdownRepository.findByIdAndIsDeletedFalse(targetId)
                    .map(com.toir.entity.PlannedShutdown::getDepartmentId);
            case REPAIR_CAMPAIGN -> repairCampaignRepository.findByIdAndIsDeletedFalse(targetId)
                    .map(com.toir.entity.repair.RepairCampaign::getDepartmentId);
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
                 PROCUREMENT, PROCUREMENT_REQUEST, BUDGET, MAINTENANCE_BUDGET, ACTUAL_COST,
                 EQUIPMENT_COMMISSIONING, PLANNED_SHUTDOWN, REPAIR_CAMPAIGN -> true;
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
