package com.toir.service;

import com.toir.dto.workorder.*;
import com.toir.dto.triad.TriadLinkMapper;
import com.toir.entity.Department;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.entity.repair.RepairRequest;
import com.toir.entity.warehouse.WarehouseEquipmentItem;
import com.toir.enums.PlanStatus;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.WarehouseEquipmentItemRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.enums.PprTaskStatus;
import com.toir.enums.RequestStatus;
import com.toir.enums.WarehouseEquipmentStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkType;

import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.util.AuditBuilderService;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkOrderService {

    private static final String MODULE = "work-order";
    private static final String ENTITY = "WorkOrder";

    private final WorkOrderRepository repository;
    private final EquipmentRepository equipmentRepository;
    private final DepartmentRepository departmentRepository;
    private final AuditBuilderService auditBuilderService;
    private final PprPlanRepository pprPlanRepository;
    private final PprTaskRepository pprTaskRepository;
    private final RepairRequestRepository repairRequestRepository;
    private final DefectRepository defectRepository;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseEquipmentItemRepository warehouseEquipmentItemRepository;
    private final WarehouseEquipmentItemService warehouseEquipmentItemService;
    private static final Set<WorkOrderStatus> FINAL_WORK_ORDER_STATUSES =
            EnumSet.of(WorkOrderStatus.COMPLETED, WorkOrderStatus.CLOSED, WorkOrderStatus.CANCELLED);
    private static final Set<RequestStatus> DISALLOWED_REPAIR_REQUEST_STATUSES_FOR_WORK_ORDER_CREATE =
            EnumSet.of(RequestStatus.REJECTED, RequestStatus.CLOSED, RequestStatus.CANCELLED);


    @Transactional(readOnly = true)
    public List<WorkOrderDto> search(WorkOrderStatus status, UUID departmentId, UUID equipmentId) {
        return toDtos(repository.search(status, departmentId, equipmentId));
    }

    @Transactional(readOnly = true)
    public Page<WorkOrderDto> search(WorkOrderStatus status, UUID departmentId, UUID equipmentId, int page, int pageSize, String search) {
        var pageable = PaginationUtils.pageRequest(page, pageSize);
        Page<WorkOrder> resultPage = repository.searchPaginated(
                status,
                departmentId,
                equipmentId,
                search,
                pageable
        );
        return toDtoPage(resultPage);
    }

    @Transactional(readOnly = true)
    public Page<WorkOrderDto> mobileFeed(UUID departmentId, UUID equipmentId, String search, int page, int pageSize) {
        Page<WorkOrder> resultPage = repository.searchMobileFeed(
                departmentId,
                equipmentId,
                search,
                PaginationUtils.pageRequest(page, pageSize)
        );
        return toDtoPage(resultPage);
    }

    @Transactional(readOnly = true)
    public WorkOrderDto findById(UUID id) {
        WorkOrder entity = getOrThrow(id);
        return toDto(entity);
    }

    @Transactional
    public WorkOrderDto create(WorkOrderRequest request) {
        if (request.equipmentId() == null) {
            throw RestException.badRequest("Equipment is required to create a work order");
        }
        WorkType effectiveWorkType = request.workType() != null ? request.workType() : WorkType.REPAIR;
        validateReplacementFields(request, effectiveWorkType);
        if (repository.existsByNumberAndIsDeletedFalse(request.number())) {
            throw RestException.conflict("Work order number already exists: " + request.number());
        }
        validateCreateRelations(request);
        reserveReplacementEquipmentOnCreate(request, effectiveWorkType);
        WorkOrder entity = new WorkOrder();
        entity.setNumber(request.number());
        entity.setTitle(request.title());
        entity.setEquipmentId(request.equipmentId());
        entity.setDepartmentId(request.departmentId());
        entity.setRepairRequestId(request.repairRequestId());
        entity.setDefectId(request.defectId());
        entity.setPprTaskId(request.pprTaskId());
        entity.setContractorId(request.contractorId());
        entity.setType(request.type());
        entity.setWorkType(effectiveWorkType);
        entity.setWarehouseId(request.warehouseId());
        entity.setReplacementEquipmentId(request.replacementEquipmentId());
        if (request.priority() != null) entity.setPriority(request.priority());
        entity.setStartPlannedAt(request.startPlannedAt());
        entity.setEndPlannedAt(request.endPlannedAt());
        entity.setCreatedById(request.createdById());
        entity.setSummary(request.summary());
        WorkOrder saved = repository.save(entity);

        auditBuilderService.log(
                "work_order",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.WORK_ORDER,
                "Создан наряд " + saved.getNumber(),
                null,
                saved);

        return toDto(saved);
    }

    @Transactional
    public WorkOrderDto approve(UUID id, UUID approverId) {
        WorkOrder entity = getOrThrow(id);
        if (entity.getStatus() != WorkOrderStatus.DRAFT && entity.getStatus() != WorkOrderStatus.PLANNED) {
            throw RestException.badRequest("Only DRAFT/PLANNED work orders can be approved");
        }
        entity.setStatus(WorkOrderStatus.APPROVED);
        entity.setApprovedById(approverId);
        ensureReplacementEquipmentReservedOnStart(entity);

        WorkOrder saved = repository.save(entity);

        auditBuilderService.log(
                "work_order",
                saved.getId().toString(),
                AuditAction.APPROVE,
                AuditModule.WORK_ORDER,
                "Утверждён наряд " + entity.getNumber(),
                entity,
                saved);

        return toDto(entity);
    }

    @Transactional
    public WorkOrderDto start(UUID id) {
        WorkOrder entity = getOrThrow(id);
        if (entity.getStatus() != WorkOrderStatus.APPROVED) {
            throw RestException.badRequest("Only approved work orders can be started");
        }
        entity.setStatus(WorkOrderStatus.IN_PROGRESS);
        entity.setStartedAt(Instant.now());
        ensureReplacementEquipmentReservedOnStart(entity);

        WorkOrder saved = repository.save(entity);

        auditBuilderService.log(
                "work_order",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.WORK_ORDER,
                "Начато выполнение наряда " + saved.getNumber(),
                entity,
                saved);

        return toDto(entity);
    }

    @Transactional
    public WorkOrderDto complete(UUID id, CompleteWorkOrderRequest request) {
        WorkOrder entity = getOrThrow(id);
        if (entity.getStatus() != WorkOrderStatus.IN_PROGRESS) {
            throw RestException.badRequest("Only in-progress work orders can be completed");
        }
        if (request.result() == null || request.result().isBlank()) {
            throw RestException.badRequest("Result is required to complete a work order");
        }
        entity.setResult(request.result());
        if (request.summary() != null && !request.summary().isBlank()) {
            entity.setSummary(request.summary());
        }
        validateCompleteRequestForReplacement(entity, request);
        entity.setStatus(WorkOrderStatus.COMPLETED);
        entity.setCompletedAt(Instant.now());
        updateReplacementEquipmentStatus(entity, WarehouseEquipmentStatus.INSTALLED);
        if (isReplacementWorkOrder(entity)) {
            assignReplacementEquipmentToWorkOrderDepartment(entity);
            warehouseEquipmentItemService.transferEquipmentToWarehouse(
                    entity.getEquipmentId(),
                    request.oldEquipmentReturnWarehouseId(),
                    WarehouseEquipmentStatus.OUT_OF_SERVICE
            );
        }
        completeLinkedPprTask(entity);

        WorkOrder saved = repository.save(entity);

        auditBuilderService.log(
                "work_order",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.WORK_ORDER,
                "Завершён наряд " + saved.getNumber(),
                entity,
                saved);
        return toDto(entity);
    }

    @Transactional
    public WorkOrderDto close(UUID id, CloseWorkOrderRequest request) {
        WorkOrder entity = getOrThrow(id);
        if (entity.getStatus() != WorkOrderStatus.COMPLETED) {
            throw RestException.badRequest("Only completed work orders can be closed");
        }
        if (request.result() == null || request.result().isBlank()) {
            throw RestException.badRequest("Result is required to close a work order");
        }
        entity.setResult(request.result());
        entity.setClosureNotes(request.closureNotes());
        entity.setStatus(WorkOrderStatus.CLOSED);
        entity.setCompletedAt(Instant.now());
        updateReplacementEquipmentStatus(entity, WarehouseEquipmentStatus.INSTALLED);
        completeLinkedPprTask(entity);
        closeLinkedRepairRequestIfReady(entity, request.result());

        WorkOrder saved = repository.save(entity);

        auditBuilderService.log(
                "work_order",
                saved.getId().toString(),
                AuditAction.CLOSE,
                AuditModule.WORK_ORDER,
                "Закрыт наряд " + saved.getNumber(),
                saved,
                null);


        return toDto(entity);
    }

    @Transactional
    public WorkOrderDto recalculateLinkedPprPlanForWorkOrder(UUID id) {
        WorkOrder entity = getOrThrow(id);
        if (entity.getPprTaskId() == null) {
            return toDto(entity);
        }
        PprTask task = pprTaskRepository.findByIdAndIsDeletedFalse(entity.getPprTaskId())
                .orElseThrow(() -> RestException.notFound("PPR task not found: " + entity.getPprTaskId()));
        recalculatePlanStatus(task.getPlan());
        return toDto(entity);
    }

    private void completeLinkedPprTask(WorkOrder workOrder) {
        if (workOrder.getPprTaskId() == null) {
            return;
        }
        PprTask task = pprTaskRepository.findByIdAndIsDeletedFalse(workOrder.getPprTaskId())
                .orElseThrow(() -> RestException.notFound("PPR task not found: " + workOrder.getPprTaskId()));
        if (task.getStatus() == PprTaskStatus.COMPLETED || task.getStatus() == PprTaskStatus.CANCELLED) {
            recalculatePlanStatus(task.getPlan());
            return;
        }
        task.setStatus(PprTaskStatus.COMPLETED);

        PprTask saved = pprTaskRepository.save(task);
        auditBuilderService.log(
                "ppr_task",
                task.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.PPR_TASK,
                "PPR task completed from linked work order " + workOrder.getNumber(),
                task,
                saved
        );
        recalculatePlanStatus(task.getPlan());
    }

    private void recalculatePlanStatus(PprPlan plan) {
        if (plan == null || plan.getId() == null) {
            return;
        }
        if (plan.getStatus() == PlanStatus.CANCELLED) {
            return;
        }
        List<PprTask> planTasks = pprTaskRepository.findAllByPlanIdAndIsDeletedFalseOrderByUpdatedAtDesc(plan.getId());
        if (planTasks.isEmpty()) {
            return;
        }

        boolean allCompleted = planTasks.stream()
                .allMatch(t -> t.getStatus() == PprTaskStatus.COMPLETED);
        boolean hasOperationalProgress = planTasks.stream()
                .anyMatch(t -> t.getStatus() == PprTaskStatus.IN_PROGRESS || t.getStatus() == PprTaskStatus.COMPLETED);

        PlanStatus newStatus = null;
        if (allCompleted) {
            newStatus = PlanStatus.CLOSED;
        } else if (hasOperationalProgress) {
            newStatus = PlanStatus.IN_PROGRESS;
        }

        if (newStatus == null || plan.getStatus() == newStatus) {
            return;
        }

        plan.setStatus(newStatus);
        PprPlan savedPlan = pprPlanRepository.save(plan);
        auditBuilderService.log(
                "ppr_plan",
                savedPlan.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.PPR_PLAN,
                "PPR plan status recalculated from child task progress",
                plan,
                savedPlan
        );
    }

    private void closeLinkedRepairRequestIfReady(WorkOrder workOrder, String closeResult) {
        if (workOrder.getRepairRequestId() == null) {
            return;
        }
        List<WorkOrder> linkedWorkOrders = repository
                .findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrder.getRepairRequestId());
        if (linkedWorkOrders.isEmpty()) {
            return;
        }
        boolean allTerminal = linkedWorkOrders.stream().allMatch(this::isTerminal);
        if (!allTerminal) {
            return;
        }
        RepairRequest request = repairRequestRepository.findByIdAndIsDeletedFalse(workOrder.getRepairRequestId())
                .orElseThrow(() -> RestException.notFound("Repair request not found: " + workOrder.getRepairRequestId()));
        if (request.getStatus() == RequestStatus.CLOSED || request.getStatus() == RequestStatus.CANCELLED) {
            return;
        }
        request.setStatus(RequestStatus.CLOSED);
        request.setActualCompletionAt(Instant.now());
        request.setCloseResult(closeResult);

        RepairRequest saved = repairRequestRepository.save(request);

        auditBuilderService.log(
                "repair_request",
                saved.getId().toString(),
                AuditAction.CLOSE,
                AuditModule.REPAIR_REQUEST,
                "Заявка " + saved.getNumber() + " закрыта после закрытия связанных нарядов",
                request,
                saved
        );
    }

    private boolean isTerminal(WorkOrder workOrder) {
        return workOrder.getStatus() == WorkOrderStatus.CLOSED
                || workOrder.getStatus() == WorkOrderStatus.CANCELLED;
    }

    private WorkOrder getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Work order not found: " + id));
    }

    private void validateCreateRelations(WorkOrderRequest request) {
        if (request.repairRequestId() != null) {
            RepairRequest repairRequest = repairRequestRepository.findByIdAndIsDeletedFalse(request.repairRequestId())
                    .orElseThrow(() -> RestException.notFound("Repair request not found: " + request.repairRequestId()));
            if (DISALLOWED_REPAIR_REQUEST_STATUSES_FOR_WORK_ORDER_CREATE.contains(repairRequest.getStatus())) {
                throw RestException.badRequest(
                        "Cannot create work order for repair request in status " + repairRequest.getStatus());
            }
        }

        if (request.defectId() == null) {
            return;
        }
        Defect defect = defectRepository.findByIdAndIsDeletedFalse(request.defectId())
                .orElseThrow(() -> RestException.notFound("Defect not found: " + request.defectId()));

        if (request.repairRequestId() == null) {
            return;
        }
        UUID defectRepairRequestId = defect.getRepairRequestId();
        if (defectRepairRequestId != null && !defectRepairRequestId.equals(request.repairRequestId())) {
            throw RestException.badRequest(
                    "Defect " + request.defectId() + " belongs to a different repair request");
        }
    }

    void ensureReplacementEquipmentReservedOnStart(WorkOrder workOrder) {
        if (!isReplacementWorkOrder(workOrder)) {
            return;
        }
        WarehouseEquipmentItem item = getReplacementWarehouseEquipmentItemOrThrow(workOrder);
        WarehouseEquipmentStatus currentStatus = item.getStatus();
        if (currentStatus == WarehouseEquipmentStatus.AVAILABLE) {
            item.setStatus(WarehouseEquipmentStatus.RESERVED);
            warehouseEquipmentItemRepository.save(item);
            return;
        }
        if (currentStatus == WarehouseEquipmentStatus.RESERVED) {
            return;
        }
        throw RestException.badRequest("Replacement equipment must be AVAILABLE or RESERVED to start work order");
    }

    private boolean isReplacementWorkOrder(WorkOrder workOrder) {
        return workOrder.getWorkType() == WorkType.REPLACEMENT;
    }

    private void updateReplacementEquipmentStatus(WorkOrder workOrder, WarehouseEquipmentStatus status) {
        if (!isReplacementWorkOrder(workOrder)) {
            return;
        }
        WarehouseEquipmentItem item = getReplacementWarehouseEquipmentItemOrThrow(workOrder);
        if (item.getStatus() == status) {
            return;
        }
        item.setStatus(status);
        warehouseEquipmentItemRepository.save(item);
    }

    private WarehouseEquipmentItem getReplacementWarehouseEquipmentItemOrThrow(WorkOrder workOrder) {
        if (workOrder.getWarehouseId() == null || workOrder.getReplacementEquipmentId() == null) {
            throw RestException.badRequest("warehouseId and replacementEquipmentId are required for replacement work orders");
        }
        return warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(
                        workOrder.getWarehouseId(),
                        workOrder.getReplacementEquipmentId()
                )
                .orElseThrow(() -> RestException.badRequest("Replacement equipment item not found in selected warehouse"));
    }

    private void validateReplacementFields(WorkOrderRequest request, WorkType effectiveWorkType) {
        if (effectiveWorkType == WorkType.REPLACEMENT) {
            if (request.warehouseId() == null) {
                throw RestException.badRequest("warehouseId is required when workType is REPLACEMENT");
            }
            if (request.replacementEquipmentId() == null) {
                throw RestException.badRequest("replacementEquipmentId is required when workType is REPLACEMENT");
            }
            if (request.replacementEquipmentId().equals(request.equipmentId())) {
                throw RestException.badRequest("replacementEquipmentId must be different from equipmentId");
            }
            warehouseRepository.findByIdAndIsDeletedFalse(request.warehouseId())
                    .orElseThrow(() -> RestException.notFound("Warehouse not found: " + request.warehouseId()));
            equipmentRepository.findByIdAndIsDeletedFalse(request.replacementEquipmentId())
                    .orElseThrow(() -> RestException.notFound("Replacement equipment not found: " + request.replacementEquipmentId()));
            WarehouseEquipmentItem warehouseEquipmentItem = warehouseEquipmentItemRepository
                    .findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(
                            request.warehouseId(),
                            request.replacementEquipmentId()
                    ).orElseThrow(() -> RestException.badRequest("Replacement equipment does not belong to selected warehouse"));
            if (warehouseEquipmentItem.getStatus() != WarehouseEquipmentStatus.AVAILABLE) {
                throw RestException.badRequest("Replacement equipment must be AVAILABLE");
            }
            if (repository.existsActiveReplacementAssignment(
                    request.replacementEquipmentId(),
                    WorkType.REPLACEMENT,
                    FINAL_WORK_ORDER_STATUSES
            )) {
                throw RestException.conflict("Replacement equipment is already assigned to another active work order");
            }
            return;
        }
        if (request.warehouseId() != null || request.replacementEquipmentId() != null) {
            throw RestException.badRequest("warehouseId and replacementEquipmentId must be null when workType is not REPLACEMENT");
        }
    }

    private void reserveReplacementEquipmentOnCreate(WorkOrderRequest request, WorkType effectiveWorkType) {
        if (effectiveWorkType != WorkType.REPLACEMENT) {
            return;
        }
        WarehouseEquipmentItem item = warehouseEquipmentItemRepository
                .findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(
                        request.warehouseId(),
                        request.replacementEquipmentId()
                )
                .orElseThrow(() -> RestException.badRequest("Replacement equipment does not belong to selected warehouse"));
        if (item.getStatus() != WarehouseEquipmentStatus.AVAILABLE) {
            throw RestException.badRequest("Replacement equipment must be AVAILABLE");
        }
        item.setStatus(WarehouseEquipmentStatus.RESERVED);
        warehouseEquipmentItemRepository.save(item);
    }

    private void validateCompleteRequestForReplacement(WorkOrder entity, CompleteWorkOrderRequest request) {
        if (isReplacementWorkOrder(entity)) {
            if (request.oldEquipmentReturnWarehouseId() == null) {
                throw RestException.badRequest("oldEquipmentReturnWarehouseId is required when workType is REPLACEMENT");
            }
            warehouseRepository.findByIdAndIsDeletedFalse(request.oldEquipmentReturnWarehouseId())
                    .orElseThrow(() -> RestException.notFound("Warehouse not found: " + request.oldEquipmentReturnWarehouseId()));
            return;
        }
        if (request.oldEquipmentReturnWarehouseId() != null) {
            throw RestException.badRequest("oldEquipmentReturnWarehouseId must be null when workType is not REPLACEMENT");
        }
    }

    private void assignReplacementEquipmentToWorkOrderDepartment(WorkOrder workOrder) {
        Equipment replacementEquipment = equipmentRepository.findByIdAndIsDeletedFalse(workOrder.getReplacementEquipmentId())
                .orElseThrow(() -> RestException.notFound("Replacement equipment not found: " + workOrder.getReplacementEquipmentId()));
        replacementEquipment.setDepartmentId(workOrder.getDepartmentId());
        equipmentRepository.save(replacementEquipment);
    }

    private WorkOrderDto toDto(WorkOrder entity) {
        RepairRequest linkedRepairRequest = entity.getRepairRequestId() == null
                ? null
                : repairRequestRepository.findByIdAndIsDeletedFalse(entity.getRepairRequestId()).orElse(null);
        Defect linkedDefect = entity.getDefectId() == null
                ? null
                : defectRepository.findByIdAndIsDeletedFalse(entity.getDefectId()).orElse(null);
        return toDto(entity, linkedRepairRequest, linkedDefect);
    }

    private WorkOrderDto toDto(WorkOrder entity, RepairRequest linkedRepairRequest, Defect linkedDefect) {
        String equipmentName = equipmentRepository.findById(entity.getEquipmentId())
                .map(Equipment::getName)
                .orElse(null);
        String departmentName = departmentRepository.findById(entity.getDepartmentId())
                .map(Department::getName)
                .orElse(null);
        String replacementEquipmentName = entity.getReplacementEquipmentId() == null
                ? null
                : equipmentRepository.findById(entity.getReplacementEquipmentId())
                .map(Equipment::getName)
                .orElse(null);
        return new WorkOrderDto(
                entity.getId(), entity.getNumber(), entity.getTitle(), entity.getEquipmentId(), entity.getDepartmentId(),
                equipmentName, departmentName,
                entity.getRepairRequestId(), entity.getDefectId(), entity.getPprTaskId(), entity.getContractorId(),
                entity.getStatus(), entity.getType(), entity.getWorkType(), entity.getPriority(),
                entity.getStartPlannedAt(), entity.getEndPlannedAt(), entity.getStartedAt(), entity.getCompletedAt(),
                entity.getSummary(), entity.getResult(), entity.getClosureNotes(),
                entity.getCreatedById(), entity.getApprovedById(),
                entity.getWarehouseId(), entity.getReplacementEquipmentId(), replacementEquipmentName,
                entity.getTasks().stream().map(WorkOrderTaskDto::from).toList(),
                TriadLinkMapper.toRepairRequestBrief(linkedRepairRequest),
                TriadLinkMapper.toDefectBrief(linkedDefect)
        );
    }

    private List<WorkOrderDto> toDtos(List<WorkOrder> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }

        List<UUID> repairRequestIds = entities.stream()
                .map(WorkOrder::getRepairRequestId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<UUID, RepairRequest> repairRequestById = repairRequestIds.isEmpty()
                ? Map.of()
                : repairRequestRepository.findAllByIdInAndIsDeletedFalse(repairRequestIds)
                .stream()
                .collect(Collectors.toMap(RepairRequest::getId, Function.identity()));

        List<UUID> defectIds = entities.stream()
                .map(WorkOrder::getDefectId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<UUID, Defect> defectById = defectIds.isEmpty()
                ? Map.of()
                : defectRepository.findAllByIdInAndIsDeletedFalse(defectIds)
                .stream()
                .collect(Collectors.toMap(Defect::getId, Function.identity()));

        return entities.stream()
                .map(entity -> toDto(
                        entity,
                        repairRequestById.get(entity.getRepairRequestId()),
                        defectById.get(entity.getDefectId())
                ))
                .toList();
    }

    private Page<WorkOrderDto> toDtoPage(Page<WorkOrder> page) {
        if (page.isEmpty()) {
            return new PageImpl<>(List.of(), page.getPageable(), page.getTotalElements());
        }
        return new PageImpl<>(toDtos(page.getContent()), page.getPageable(), page.getTotalElements());
    }
}

