package com.toir.service.attachment;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.CompletionAct;
import com.toir.entity.StockMovement;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairRequest;
import com.toir.entity.warehouse.Warehouse;
import com.toir.enums.AttachmentTargetType;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.FileCategory;
import com.toir.exception.RestException;
import com.toir.repository.ApprovalRequestRepository;
import com.toir.repository.CompletionActRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.security.ScopeAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttachmentTargetAccessService {

    private final EquipmentRepository equipmentRepository;
    private final WorkOrderRepository workOrderRepository;
    private final RepairRequestRepository repairRequestRepository;
    private final CompletionActRepository completionActRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final StockMovementRepository stockMovementRepository;
    private final WarehouseRepository warehouseRepository;
    private final ScopeAccessService scopeAccessService;

    public AttachmentTargetType assertCanAccess(AttachmentTargetType targetType, UUID targetId) {
        if (targetType == null) {
            throw RestException.badRequest("targetType is required");
        }
        if (targetId == null) {
            throw RestException.badRequest("targetId is required");
        }
        switch (targetType) {
            case EQUIPMENT -> assertCanAccessEquipment(targetId, false);
            case VEHICLE -> assertCanAccessEquipment(targetId, true);
            case WORK_ORDER -> assertCanAccessWorkOrder(targetId);
            case REPAIR_REQUEST -> assertCanAccessRepairRequest(targetId);
            case COMPLETION_ACT -> assertCanAccessCompletionAct(targetId);
            case APPROVAL -> assertCanAccessApproval(targetId);
            case STOCK_MOVEMENT -> assertCanAccessStockMovement(targetId);
        }
        return targetType;
    }

    public FileCategory fileCategoryFor(AttachmentTargetType targetType) {
        return switch (targetType) {
            case EQUIPMENT -> FileCategory.EQUIPMENT_DOCUMENT;
            case VEHICLE -> FileCategory.VEHICLE_DOCUMENT;
            case WORK_ORDER -> FileCategory.WORK_ORDER_DOCUMENT;
            case STOCK_MOVEMENT -> FileCategory.STOCK_MOVEMENT_DOCUMENT;
            case REPAIR_REQUEST, COMPLETION_ACT, APPROVAL -> FileCategory.DOCUMENT;
        };
    }

    private Equipment assertCanAccessEquipment(UUID equipmentId, boolean vehicleOnly) {
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + equipmentId));
        if (vehicleOnly && equipment.getCategory() != EquipmentCategory.VEHICLE) {
            throw RestException.badRequest("Equipment is not a vehicle: " + equipmentId);
        }
        scopeAccessService.assertCanAccessEquipmentScope(
                equipment.getResponsibleDepartmentId(),
                equipment.getDepartmentId()
        );
        return equipment;
    }

    private WorkOrder assertCanAccessWorkOrder(UUID workOrderId) {
        WorkOrder workOrder = workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)
                .orElseThrow(() -> RestException.notFound("Work order not found: " + workOrderId));
        if (scopeAccessService.isScopeAdmin()) {
            return workOrder;
        }
        if (scopeAccessService.canAccessDepartment(workOrder.getDepartmentId())) {
            return workOrder;
        }
        if (workOrder.getPerformer() != null
                && scopeAccessService.canAccessAssignedUser(workOrder.getPerformer().getUserId())) {
            return workOrder;
        }
        throw new AccessDeniedException("Access denied by work order department or performer scope");
    }

    private void assertCanAccessRepairRequest(UUID requestId) {
        RepairRequest request = repairRequestRepository.findByIdAndIsDeletedFalse(requestId)
                .orElseThrow(() -> RestException.notFound("Repair request not found: " + requestId));
        if (scopeAccessService.isScopeAdmin()
                || scopeAccessService.canAccessDepartment(request.getDepartmentId())
                || scopeAccessService.canAccessAssignedUser(request.getReporterId())
                || scopeAccessService.canAccessAssignedUser(request.getAssignedToId())) {
            return;
        }
        throw new AccessDeniedException("Access denied by repair request scope");
    }

    private void assertCanAccessCompletionAct(UUID actId) {
        CompletionAct act = completionActRepository.findByIdAndIsDeletedFalse(actId)
                .orElseThrow(() -> RestException.notFound("Completion act not found: " + actId));
        assertCanAccessWorkOrder(act.getWorkOrderId());
    }

    private void assertCanAccessApproval(UUID approvalId) {
        ApprovalRequest approval = approvalRequestRepository.findByIdAndIsDeletedFalse(approvalId)
                .orElseThrow(() -> RestException.notFound("Approval request not found: " + approvalId));
        UUID currentUserId = scopeAccessService.currentUserIdOrNull();
        if (scopeAccessService.isScopeAdmin()
                || Objects.equals(approval.getRequesterId(), currentUserId)
                || isCurrentApprovalParticipant(approval, currentUserId)) {
            return;
        }
        if (approval.getTargetType() != null && approval.getTargetId() != null) {
            AttachmentTargetType mappedTargetType = attachmentTargetTypeOrNull(approval.getTargetType().name());
            if (mappedTargetType != null) {
                assertCanAccess(mappedTargetType, approval.getTargetId());
                return;
            }
        }
        throw new AccessDeniedException("Access denied by approval scope");
    }

    private boolean isCurrentApprovalParticipant(ApprovalRequest approval, UUID currentUserId) {
        return currentUserId != null
                && approval.getSteps() != null
                && approval.getSteps().stream()
                .anyMatch(step -> Objects.equals(step.getApproverId(), currentUserId)
                        || Objects.equals(step.getDelegatedForId(), currentUserId)
                        || Objects.equals(step.getDecidedById(), currentUserId));
    }

    private AttachmentTargetType attachmentTargetTypeOrNull(String targetType) {
        try {
            return AttachmentTargetType.from(targetType);
        } catch (RestException ex) {
            return null;
        }
    }

    private void assertCanAccessStockMovement(UUID movementId) {
        StockMovement movement = stockMovementRepository.findByIdAndIsDeletedFalse(movementId)
                .orElseThrow(() -> RestException.notFound("Stock movement not found: " + movementId));
        Warehouse warehouse = warehouseRepository.findByIdAndIsDeletedFalse(movement.getWarehouseId())
                .orElseThrow(() -> RestException.notFound("Warehouse not found: " + movement.getWarehouseId()));
        if (scopeAccessService.isScopeAdmin()
                || (warehouse.getDepartmentId() != null && scopeAccessService.canAccessDepartment(warehouse.getDepartmentId()))
                || (warehouse.getResponsibleId() != null && scopeAccessService.canAccessEmployee(warehouse.getResponsibleId()))) {
            return;
        }
        throw new AccessDeniedException("Access denied by warehouse scope");
    }
}
