package com.toir.service.equipment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.approval.ApprovalRequestDto;
import com.toir.dto.approval.ApprovalStartRequest;
import com.toir.dto.equipmentcommissioning.EquipmentCommissioningActDto;
import com.toir.dto.equipmentcommissioning.EquipmentCommissioningActRequest;
import com.toir.entity.StockMovement;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentCommissioningAct;
import com.toir.entity.equipment.EquipmentLocationHistory;
import com.toir.entity.warehouse.WarehouseEquipmentItem;
import com.toir.enums.*;
import com.toir.exception.RestException;
import com.toir.repository.*;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentCommissioningActRepository;
import com.toir.repository.equipment.EquipmentLocationHistoryRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.ApprovalService;
import com.toir.service.maintanance.MaintenanceAutomationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EquipmentCommissioningActService {

    private static final EnumSet<WarehouseEquipmentStatus> COMMISSIONABLE_WAREHOUSE_STATUSES =
            EnumSet.of(WarehouseEquipmentStatus.AVAILABLE, WarehouseEquipmentStatus.RESERVED);
    private static final EnumSet<EquipmentCommissioningStatus> OPEN_ACT_STATUSES =
            EnumSet.of(EquipmentCommissioningStatus.DRAFT, EquipmentCommissioningStatus.PENDING_APPROVAL);

    private final EquipmentCommissioningActRepository repository;
    private final EquipmentRepository equipmentRepository;
    private final WarehouseEquipmentItemRepository warehouseItemRepository;
    private final WarehouseRepository warehouseRepository;
    private final DepartmentRepository departmentRepository;
    private final LocationRepository locationRepository;
    private final EmployeeRepository employeeRepository;
    private final EquipmentLocationHistoryRepository locationHistoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final EquipmentStatusLifecycleService statusLifecycleService;
    private final MaintenanceAutomationService maintenanceAutomationService;
    private final ScopeAccessService scopeAccessService;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<ApprovalService> approvalServiceProvider;

    @Transactional
    public EquipmentCommissioningActDto create(EquipmentCommissioningActRequest request) {
        if (repository.existsByEquipmentIdAndStatusInAndIsDeletedFalse(
                request.equipmentId(), OPEN_ACT_STATUSES)) {
            throw RestException.conflict(
                    "An open commissioning act already exists for equipment " + request.equipmentId());
        }
        validateReferences(request);
        if (repository.existsByActNumberAndIsDeletedFalse(request.actNumber().trim())) {
            throw RestException.conflict("Commissioning act number already exists: " + request.actNumber());
        }
        WarehouseEquipmentItem item = resolveWarehouseItem(request);
        EquipmentCommissioningAct act = new EquipmentCommissioningAct();
        apply(act, request, item);
        return dto(repository.save(act));
    }

    @Transactional
    public EquipmentCommissioningActDto update(UUID id, EquipmentCommissioningActRequest request) {
        EquipmentCommissioningAct act = getOrThrow(id);
        if (act.getStatus() != EquipmentCommissioningStatus.DRAFT) {
            throw RestException.conflict("Only DRAFT commissioning acts can be updated");
        }
        validateReferences(request);
        if (repository.existsByActNumberAndIdNotAndIsDeletedFalse(request.actNumber().trim(), id)) {
            throw RestException.conflict("Commissioning act number already exists: " + request.actNumber());
        }
        apply(act, request, resolveWarehouseItem(request));
        return dto(repository.save(act));
    }

    @Transactional
    public EquipmentCommissioningActDto submit(UUID id) {
        EquipmentCommissioningAct act = getOrThrow(id);
        if (act.getStatus() != EquipmentCommissioningStatus.DRAFT) {
            throw RestException.conflict("Only DRAFT commissioning acts can be submitted");
        }
        validateReadyForApproval(act);
        act.setStatus(EquipmentCommissioningStatus.PENDING_APPROVAL);
        act.setSubmittedAt(Instant.now());
        repository.saveAndFlush(act);
        try {
            ApprovalRequestDto approval = approvalServiceProvider.getObject().requestApproval(new ApprovalStartRequest(
                    ApprovalTargetType.EQUIPMENT_COMMISSIONING,
                    act.getId(),
                    ApprovalActionType.APPROVE,
                    "Equipment commissioning act " + act.getActNumber()
            ));
            act.setApprovalRequestId(approval.id());
            return dto(repository.save(act));
        } catch (RuntimeException ex) {
            act.setStatus(EquipmentCommissioningStatus.DRAFT);
            act.setSubmittedAt(null);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public Page<EquipmentCommissioningActDto> search(EquipmentCommissioningStatus status,
                                                     UUID equipmentId,
                                                     UUID departmentId,
                                                     String search,
                                                     int page,
                                                     int size) {
        UUID scopedDepartment = scopeAccessService.enforceDepartmentScope(departmentId);
        String pattern = search == null || search.isBlank() ? null : "%" + search.trim().toLowerCase() + "%";
        return repository.search(status, equipmentId, scopedDepartment, pattern,
                        PageRequest.of(Math.max(0, page), Math.max(1, size)))
                .map(this::dto);
    }

    @Transactional(readOnly = true)
    public EquipmentCommissioningActDto findById(UUID id) {
        EquipmentCommissioningAct act = getOrThrow(id);
        assertScope(act);
        return dto(act);
    }

    @Transactional
    public EquipmentCommissioningActDto cancel(UUID id) {
        EquipmentCommissioningAct act = getOrThrow(id);
        if (act.getStatus() != EquipmentCommissioningStatus.DRAFT
                && act.getStatus() != EquipmentCommissioningStatus.PENDING_APPROVAL) {
            throw RestException.conflict("Only DRAFT or PENDING_APPROVAL commissioning acts can be cancelled");
        }
        if (act.getStatus() == EquipmentCommissioningStatus.PENDING_APPROVAL && act.getApprovalRequestId() != null) {
            approvalServiceProvider.getObject().cancel(act.getApprovalRequestId());
        }
        act.setStatus(EquipmentCommissioningStatus.CANCELLED);
        return dto(repository.save(act));
    }

    @Transactional
    public void finalizeApproval(UUID id, UUID approvedBy) {
        EquipmentCommissioningAct act = getOrThrow(id);
        if (act.getStatus() == EquipmentCommissioningStatus.APPROVED) return;
        if (act.getStatus() != EquipmentCommissioningStatus.PENDING_APPROVAL) {
            throw RestException.conflict("Commissioning act is not pending approval");
        }

        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(act.getEquipmentId())
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + act.getEquipmentId()));
        WarehouseEquipmentItem item = warehouseItemRepository
                .findByIdAndIsDeletedFalse(act.getWarehouseItemId())
                .orElseThrow(() -> RestException.conflict(
                        "Source warehouse item no longer exists: " + act.getWarehouseItemId()));
        validateWarehouseItem(item, act.getSourceWarehouseId(), act.getEquipmentId());
        if (equipment.getStatus() != EquipmentStatus.STANDBY
                || equipment.getCurrentLocationType() != EquipmentLocationType.WAREHOUSE
                || !act.getSourceWarehouseId().equals(equipment.getCurrentWarehouseId())) {
            throw RestException.conflict("Equipment must remain STANDBY in the source warehouse until approval");
        }

        act.setStatus(EquipmentCommissioningStatus.APPROVED);
        act.setApprovedAt(Instant.now());
        act.setApprovedBy(approvedBy);
        repository.saveAndFlush(act);

        UUID fromWarehouseId = equipment.getCurrentWarehouseId();
        equipment.setDepartmentId(act.getTargetDepartmentId());
        equipment.setLocationId(act.getTargetLocationId());
        equipment.setCurrentLocationType(EquipmentLocationType.DEPARTMENT);
        equipment.setCurrentWarehouseId(null);
        equipment.setResponsibleDepartmentId(act.getTargetDepartmentId());
        equipment.setResponsibleId(act.getResponsibleEmployeeId());
        equipment.setCommissionedAt(act.getCommissionedAt());
        equipment.setOperationStartDate(act.getOperationStartDate());
        equipmentRepository.save(equipment);

        item.setStatus(WarehouseEquipmentStatus.INSTALLED);
        item.setActive(false);
        warehouseItemRepository.save(item);

        StockMovement movement = new StockMovement();
        movement.setWarehouseId(act.getSourceWarehouseId());
        movement.setEquipmentTypeId(equipment.getEquipmentTypeId());
        movement.setType(StockMovementType.EQUIPMENT_OUT);
        movement.setQuantity(1);
        movement.setUnit("PCS");
        movement.setDocumentNumber(act.getActNumber());
        movement.setMovementDate(act.getActDate());
        movement.setResponsiblePersonId(act.getResponsibleEmployeeId());
        movement.setDepartmentId(act.getTargetDepartmentId());
        movement.setSourceType(StockMovementSourceType.EQUIPMENT_COMMISSIONING);
        movement.setSourceId(act.getId());
        movement.setNotes("Equipment commissioned: " + equipment.getCode());
        StockMovement savedMovement = stockMovementRepository.save(movement);
        act.setWarehouseMovementId(savedMovement.getId());
        repository.save(act);

        statusLifecycleService.recordSystemTransition(
                equipment.getId(), EquipmentStatus.ACTIVE,
                "Approved equipment commissioning act " + act.getActNumber(),
                "EQUIPMENT_COMMISSIONING", act.getId());
        writeLocationHistory(equipment, fromWarehouseId, approvedBy, act);
        maintenanceAutomationService.evaluateEquipment(equipment.getId(), MaintenanceTriggerSource.MANUAL_RECALCULATION);
    }

    @Transactional
    public void finalizeRejection(UUID id, String reason) {
        EquipmentCommissioningAct act = getOrThrow(id);
        if (act.getStatus() == EquipmentCommissioningStatus.REJECTED) return;
        if (act.getStatus() != EquipmentCommissioningStatus.PENDING_APPROVAL) {
            throw RestException.conflict("Commissioning act is not pending approval");
        }
        act.setStatus(EquipmentCommissioningStatus.REJECTED);
        act.setRejectedAt(Instant.now());
        act.setRejectionReason(reason);
        repository.save(act);
    }

    public void validateCanApprove(UUID id) {
        EquipmentCommissioningAct act = getOrThrow(id);
        if (act.getStatus() != EquipmentCommissioningStatus.PENDING_APPROVAL) {
            throw RestException.conflict("Commissioning act must be PENDING_APPROVAL");
        }
        validateReadyForApproval(act);
    }

    private void apply(EquipmentCommissioningAct act,
                       EquipmentCommissioningActRequest request,
                       WarehouseEquipmentItem item) {
        act.setEquipmentId(request.equipmentId());
        act.setSourceWarehouseId(request.sourceWarehouseId());
        act.setWarehouseItemId(item.getId());
        act.setTargetDepartmentId(request.targetDepartmentId());
        act.setTargetLocationId(request.targetLocationId());
        act.setResponsibleEmployeeId(request.responsibleEmployeeId());
        act.setActNumber(request.actNumber().trim());
        act.setActDate(request.actDate());
        act.setCommissionedAt(request.commissionedAt());
        act.setOperationStartDate(request.operationStartDate());
        act.setCommitteeJson(toJson(request.committee()));
        act.setNotes(request.notes());
    }

    private void validateReferences(EquipmentCommissioningActRequest request) {
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(request.equipmentId())
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + request.equipmentId()));
        scopeAccessService.assertCanAccessEquipmentScope(
                equipment.getResponsibleDepartmentId(), equipment.getDepartmentId());
        warehouseRepository.findByIdAndIsDeletedFalse(request.sourceWarehouseId())
                .orElseThrow(() -> RestException.notFound("Warehouse not found: " + request.sourceWarehouseId()));
        departmentRepository.findByIdAndIsDeletedFalse(request.targetDepartmentId())
                .orElseThrow(() -> RestException.notFound("Department not found: " + request.targetDepartmentId()));
        if (request.targetLocationId() != null) {
            var location = locationRepository.findByIdAndIsDeletedFalse(request.targetLocationId())
                    .orElseThrow(() -> RestException.notFound("Location not found: " + request.targetLocationId()));
            if (location.getDepartmentId() != null
                    && !request.targetDepartmentId().equals(location.getDepartmentId())) {
                throw RestException.badRequest("Target location does not belong to target department");
            }
        }
        employeeRepository.findByIdAndIsDeletedFalse(request.responsibleEmployeeId())
                .filter(employee -> employee.isActive())
                .orElseThrow(() -> RestException.badRequest("Responsible employee must be active"));
        if (request.operationStartDate().isBefore(request.commissionedAt())) {
            throw RestException.badRequest("operationStartDate cannot be before commissionedAt");
        }
    }

    private WarehouseEquipmentItem resolveWarehouseItem(EquipmentCommissioningActRequest request) {
        WarehouseEquipmentItem item;
        if (request.warehouseItemId() != null) {
            item = warehouseItemRepository.findByIdAndIsDeletedFalse(request.warehouseItemId())
                    .orElseThrow(() -> RestException.notFound(
                            "Warehouse equipment item not found: " + request.warehouseItemId()));
        } else {
            item = warehouseItemRepository
                    .findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(
                            request.sourceWarehouseId(), request.equipmentId())
                    .orElseThrow(() -> RestException.conflict(
                            "No active warehouse item found for equipment " + request.equipmentId()
                                    + " in warehouse " + request.sourceWarehouseId()));
        }
        validateWarehouseItem(item, request.sourceWarehouseId(), request.equipmentId());
        return item;
    }

    private void validateReadyForApproval(EquipmentCommissioningAct act) {
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(act.getEquipmentId())
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + act.getEquipmentId()));
        if (equipment.getStatus() != EquipmentStatus.STANDBY
                || equipment.getCurrentLocationType() != EquipmentLocationType.WAREHOUSE
                || !act.getSourceWarehouseId().equals(equipment.getCurrentWarehouseId())) {
            throw RestException.conflict("Equipment must be STANDBY in the source warehouse");
        }
        WarehouseEquipmentItem item = warehouseItemRepository
                .findByIdAndIsDeletedFalse(act.getWarehouseItemId())
                .orElseThrow(() -> RestException.conflict(
                        "Source warehouse item no longer exists: " + act.getWarehouseItemId()));
        validateWarehouseItem(item, act.getSourceWarehouseId(), act.getEquipmentId());
    }

    private void validateWarehouseItem(WarehouseEquipmentItem item,
                                       UUID expectedWarehouseId,
                                       UUID expectedEquipmentId) {
        if (!Objects.equals(item.getWarehouseId(), expectedWarehouseId)) {
            throw RestException.conflict(
                    "Warehouse item belongs to warehouse " + item.getWarehouseId()
                            + ", not source warehouse " + expectedWarehouseId);
        }
        if (!Objects.equals(item.getEquipmentId(), expectedEquipmentId)) {
            throw RestException.conflict(
                    "Warehouse item belongs to equipment " + item.getEquipmentId()
                            + ", not equipment " + expectedEquipmentId);
        }
        if (!item.isActive()) {
            throw RestException.conflict("Warehouse equipment item is not active: " + item.getId());
        }
        if (!COMMISSIONABLE_WAREHOUSE_STATUSES.contains(item.getStatus())) {
            throw RestException.conflict(
                    "Warehouse equipment status must be AVAILABLE or RESERVED, but was " + item.getStatus());
        }
    }

    private void writeLocationHistory(Equipment equipment,
                                      UUID fromWarehouseId,
                                      UUID changedBy,
                                      EquipmentCommissioningAct act) {
        EquipmentLocationHistory history = new EquipmentLocationHistory();
        history.setEquipmentId(equipment.getId());
        history.setFromLocationType(EquipmentLocationType.WAREHOUSE);
        history.setFromWarehouseId(fromWarehouseId);
        history.setToLocationType(EquipmentLocationType.DEPARTMENT);
        history.setToDepartmentId(act.getTargetDepartmentId());
        history.setResponsibleDepartmentId(act.getTargetDepartmentId());
        history.setChangedBy(changedBy);
        history.setChangedAt(Instant.now());
        history.setNote("Approved commissioning act " + act.getActNumber());
        locationHistoryRepository.save(history);
    }

    private EquipmentCommissioningAct getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Equipment commissioning act not found: " + id));
    }

    private void assertScope(EquipmentCommissioningAct act) {
        scopeAccessService.assertCanAccessDepartment(act.getTargetDepartmentId());
    }

    private String toJson(List<EquipmentCommissioningActRequest.Signatory> committee) {
        try {
            return objectMapper.writeValueAsString(committee == null ? List.of() : committee);
        } catch (Exception ex) {
            throw RestException.badRequest("Invalid commissioning committee");
        }
    }

    private EquipmentCommissioningActDto dto(EquipmentCommissioningAct act) {
        return EquipmentCommissioningActDto.from(act, objectMapper);
    }
}
