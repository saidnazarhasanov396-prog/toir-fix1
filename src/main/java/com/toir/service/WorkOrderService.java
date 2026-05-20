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
import com.toir.repository.WorkExecutionRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.repository.repair.RepairMaterialUsageRepository;
import com.toir.enums.DefectStatus;
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
    private final WorkExecutionRepository workExecutionRepository;
    private final RepairMaterialUsageRepository repairMaterialUsageRepository;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseEquipmentItemRepository warehouseEquipmentItemRepository;
    private final WarehouseEquipmentItemService warehouseEquipmentItemService;
    private static final Set<WorkOrderStatus> TERMINAL_WORK_ORDER_STATUSES = EnumSet.of(WorkOrderStatus.COMPLETED,
            WorkOrderStatus.CLOSED, WorkOrderStatus.CANCELLED);
    private static final Set<WorkOrderStatus> ACTIVE_WORK_ORDER_STATUSES = EnumSet.of(WorkOrderStatus.DRAFT,
            WorkOrderStatus.PLANNED, WorkOrderStatus.APPROVED, WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.SUSPENDED);
    private static final Set<RequestStatus> TERMINAL_REPAIR_REQUEST_STATUSES = EnumSet.of(RequestStatus.REJECTED,
            RequestStatus.COMPLETED, RequestStatus.CLOSED, RequestStatus.CANCELLED);
    private static final Set<DefectStatus> TERMINAL_DEFECT_STATUSES = EnumSet.of(DefectStatus.RESOLVED,
            DefectStatus.CLOSED, DefectStatus.CANCELLED);
    private static final Set<DefectStatus> RESOLVED_OR_CLOSED_DEFECT_STATUSES = EnumSet.of(DefectStatus.RESOLVED,
            DefectStatus.CLOSED);
    private static final Set<RequestStatus> DISALLOWED_REPAIR_REQUEST_STATUSES_FOR_WORK_ORDER_CREATE = EnumSet
            .of(RequestStatus.REJECTED, RequestStatus.CLOSED, RequestStatus.CANCELLED);

    @Transactional(readOnly = true)
    public List<WorkOrderDto> search(WorkOrderStatus status, UUID departmentId, UUID equipmentId) {
        return toDtos(repository.search(status, departmentId, equipmentId));
    }

    @Transactional(readOnly = true)
    public Page<WorkOrderDto> search(WorkOrderStatus status, UUID departmentId, UUID equipmentId, int page,
            int pageSize, String search) {
        var pageable = PaginationUtils.pageRequest(page, pageSize);
        String normalizedSearch = normalizeSearch(search);
        Page<WorkOrder> resultPage = repository.searchPaginated(
                status,
                departmentId,
                equipmentId,
                normalizedSearch,
                pageable);
        return toDtoPage(resultPage);
    }

    @Transactional(readOnly = true)
    public WorkOrderStatsResponse getStats(WorkOrderStatus status, UUID departmentId, UUID equipmentId, String search) {
        String statusStr = status == null ? null : status.name();
        String normalizedSearch = normalizeSearch(search);
        var stats = repository.getWorkOrderStats(statusStr, departmentId, equipmentId, normalizedSearch);
        return new WorkOrderStatsResponse(
                stats.getTotalOrders() == null ? 0 : stats.getTotalOrders(),
                stats.getOpenOrders() == null ? 0 : stats.getOpenOrders(),
                stats.getCompletedOrders() == null ? 0 : stats.getCompletedOrders(),
                stats.getOverdueOrders() == null ? 0 : stats.getOverdueOrders());
    }

    @Transactional(readOnly = true)
    public Page<WorkOrderDto> mobileFeed(UUID departmentId, UUID equipmentId, String search, int page, int pageSize) {
        Page<WorkOrder> resultPage = repository.searchMobileFeed(
                departmentId,
                equipmentId,
                search,
                PaginationUtils.pageRequest(page, pageSize));
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
        if (request.priority() != null)
            entity.setPriority(request.priority());
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
        syncLinkedOnStart(saved);

        auditBuilderService.log(
                "work_order",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.WORK_ORDER,
                "Начато выполнение наряда " + saved.getNumber(),
                entity,
                saved);

        return toDto(saved);
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
                    WarehouseEquipmentStatus.OUT_OF_SERVICE);
        }
        completeLinkedPprTask(entity);

        WorkOrder saved = repository.save(entity);
        syncLinkedOnComplete(saved);

        auditBuilderService.log(
                "work_order",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.WORK_ORDER,
                "Завершён наряд " + saved.getNumber(),
                entity,
                saved);
        return toDto(saved);
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

        WorkOrder saved = repository.save(entity);
        syncLinkedOnClose(saved, request.result());

        auditBuilderService.log(
                "work_order",
                saved.getId().toString(),
                AuditAction.CLOSE,
                AuditModule.WORK_ORDER,
                "Закрыт наряд " + saved.getNumber(),
                saved,
                null);

        return toDto(saved);
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
                saved);
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
                savedPlan);
    }

    private void syncLinkedOnStart(WorkOrder workOrder) {
        syncRepairRequestOnStart(workOrder);
        syncDefectOnStart(workOrder);
    }

    private void syncLinkedOnComplete(WorkOrder workOrder) {
        syncDefectOnComplete(workOrder);
        syncRepairRequestOnComplete(workOrder);
    }

    private void syncLinkedOnClose(WorkOrder workOrder, String closeResult) {
        syncDefectOnClose(workOrder);
        syncRepairRequestOnClose(workOrder, closeResult);
    }

    private void syncRepairRequestOnStart(WorkOrder workOrder) {
        if (workOrder.getRepairRequestId() == null) {
            return;
        }
        repairRequestRepository.findByIdAndIsDeletedFalse(workOrder.getRepairRequestId())
                .ifPresent(request -> {
                    if (TERMINAL_REPAIR_REQUEST_STATUSES.contains(request.getStatus())) {
                        return;
                    }
                    if (request.getStatus() == RequestStatus.IN_PROGRESS) {
                        return;
                    }
                    request.setStatus(RequestStatus.IN_PROGRESS);
                    RepairRequest saved = repairRequestRepository.save(request);
                    auditBuilderService.log(
                            "repair_request",
                            saved.getId().toString(),
                            AuditAction.UPDATE,
                            AuditModule.REPAIR_REQUEST,
                            "Repair request moved to IN_PROGRESS from linked work order start",
                            request,
                            saved);
                });
    }

    private void syncDefectOnStart(WorkOrder workOrder) {
        if (workOrder.getDefectId() == null) {
            return;
        }
        defectRepository.findByIdAndIsDeletedFalse(workOrder.getDefectId())
                .ifPresent(defect -> {
                    if (TERMINAL_DEFECT_STATUSES.contains(defect.getStatus())) {
                        return;
                    }
                    if (defect.getStatus() == DefectStatus.IN_PROGRESS) {
                        return;
                    }
                    defect.setStatus(DefectStatus.IN_PROGRESS);
                    Defect saved = defectRepository.save(defect);
                    auditBuilderService.log(
                            "defect",
                            saved.getId().toString(),
                            AuditAction.UPDATE,
                            AuditModule.DEFECT,
                            "Defect moved to IN_PROGRESS from linked work order start",
                            defect,
                            saved);
                });
    }

    private void syncDefectOnComplete(WorkOrder workOrder) {
        if (workOrder.getDefectId() == null) {
            return;
        }
        defectRepository.findByIdAndIsDeletedFalse(workOrder.getDefectId())
                .ifPresent(defect -> {
                    if (TERMINAL_DEFECT_STATUSES.contains(defect.getStatus())) {
                        return;
                    }
                    if (hasActiveWorkOrderForDefect(defect.getId())) {
                        return;
                    }
                    defect.setStatus(DefectStatus.RESOLVED);
                    defect.setResolvedAt(Instant.now());
                    Defect saved = defectRepository.save(defect);
                    auditBuilderService.log(
                            "defect",
                            saved.getId().toString(),
                            AuditAction.UPDATE,
                            AuditModule.DEFECT,
                            "Defect resolved after linked work orders became non-active",
                            defect,
                            saved);
                });
    }

    private void syncRepairRequestOnComplete(WorkOrder workOrder) {
        if (workOrder.getRepairRequestId() == null) {
            return;
        }
        repairRequestRepository.findByIdAndIsDeletedFalse(workOrder.getRepairRequestId())
                .ifPresent(request -> {
                    if (!allWorkOrdersTerminalForRepairRequest(request.getId())) {
                        return;
                    }
                    if (!allDefectsResolvedOrClosedForRepairRequest(request.getId())) {
                        return;
                    }
                    if (TERMINAL_REPAIR_REQUEST_STATUSES.contains(request.getStatus())) {
                        return;
                    }
                    request.setStatus(RequestStatus.COMPLETED);
                    RepairRequest saved = repairRequestRepository.save(request);
                    auditBuilderService.log(
                            "repair_request",
                            saved.getId().toString(),
                            AuditAction.UPDATE,
                            AuditModule.REPAIR_REQUEST,
                            "Repair request moved to COMPLETED after linked work order completion",
                            request,
                            saved);
                });
    }

    private void syncDefectOnClose(WorkOrder workOrder) {
        if (workOrder.getDefectId() == null) {
            return;
        }
        defectRepository.findByIdAndIsDeletedFalse(workOrder.getDefectId())
                .ifPresent(defect -> {
                    if (!allWorkOrdersTerminalForDefect(defect.getId())) {
                        return;
                    }
                    if (defect.getStatus() == DefectStatus.CLOSED) {
                        return;
                    }
                    if (defect.getStatus() != DefectStatus.RESOLVED) {
                        return;
                    }
                    defect.setStatus(DefectStatus.CLOSED);
                    Defect saved = defectRepository.save(defect);
                    auditBuilderService.log(
                            "defect",
                            saved.getId().toString(),
                            AuditAction.CLOSE,
                            AuditModule.DEFECT,
                            "Defect closed after linked work orders reached terminal state",
                            defect,
                            saved);
                });
    }

    private void syncRepairRequestOnClose(WorkOrder workOrder, String closeResult) {
        if (workOrder.getRepairRequestId() == null) {
            return;
        }
        repairRequestRepository.findByIdAndIsDeletedFalse(workOrder.getRepairRequestId())
                .ifPresent(request -> {
                    if (!allWorkOrdersTerminalForRepairRequest(request.getId())) {
                        return;
                    }
                    if (!allDefectsResolvedOrClosedForRepairRequest(request.getId())) {
                        return;
                    }
                    if (request.getStatus() == RequestStatus.CLOSED
                            || request.getStatus() == RequestStatus.CANCELLED
                            || request.getStatus() == RequestStatus.REJECTED) {
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
                            saved);
                });
    }

    private boolean hasActiveWorkOrderForDefect(UUID defectId) {
        List<WorkOrder> linkedWorkOrders = repository.findAllByDefectIdAndIsDeletedFalseOrderByUpdatedAtDesc(defectId);
        return linkedWorkOrders.stream()
                .anyMatch(linkedWorkOrder -> ACTIVE_WORK_ORDER_STATUSES.contains(linkedWorkOrder.getStatus()));
    }

    private boolean allWorkOrdersTerminalForRepairRequest(UUID repairRequestId) {
        List<WorkOrder> linkedWorkOrders = repository
                .findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(repairRequestId);
        if (linkedWorkOrders.isEmpty()) {
            return false;
        }
        return linkedWorkOrders.stream()
                .allMatch(linkedWorkOrder -> TERMINAL_WORK_ORDER_STATUSES.contains(linkedWorkOrder.getStatus()));
    }

    private boolean allWorkOrdersTerminalForDefect(UUID defectId) {
        List<WorkOrder> linkedWorkOrders = repository
                .findAllByDefectIdAndIsDeletedFalseOrderByUpdatedAtDesc(defectId);
        if (linkedWorkOrders.isEmpty()) {
            return false;
        }
        return linkedWorkOrders.stream()
                .allMatch(linkedWorkOrder -> TERMINAL_WORK_ORDER_STATUSES.contains(linkedWorkOrder.getStatus()));
    }

    private boolean allDefectsResolvedOrClosedForRepairRequest(UUID repairRequestId) {
        List<Defect> linkedDefects = defectRepository
                .findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(repairRequestId);
        return linkedDefects.stream()
                .allMatch(defect -> RESOLVED_OR_CLOSED_DEFECT_STATUSES.contains(defect.getStatus()));
    }

    private WorkOrder getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Work order not found: " + id));
    }

    private void validateCreateRelations(WorkOrderRequest request) {
        if (request.repairRequestId() != null) {
            RepairRequest repairRequest = repairRequestRepository.findByIdAndIsDeletedFalse(request.repairRequestId())
                    .orElseThrow(
                            () -> RestException.notFound("Repair request not found: " + request.repairRequestId()));
            if (DISALLOWED_REPAIR_REQUEST_STATUSES_FOR_WORK_ORDER_CREATE.contains(repairRequest.getStatus())) {
                throw RestException.badRequest(
                        "Cannot create work order for repair request in status " + repairRequest.getStatus());
            }
            if (request.equipmentId() != null
                    && repairRequest.getEquipmentId() != null
                    && !request.equipmentId().equals(repairRequest.getEquipmentId())) {
                throw RestException.badRequest("Repair request belongs to a different equipment");
            }
        }

        if (request.defectId() == null) {
            return;
        }
        Defect defect = defectRepository.findByIdAndIsDeletedFalse(request.defectId())
                .orElseThrow(() -> RestException.notFound("Defect not found: " + request.defectId()));

        if (request.equipmentId() != null
                && defect.getEquipmentId() != null
                && !request.equipmentId().equals(defect.getEquipmentId())) {
            throw RestException.badRequest(
                    "Defect " + request.defectId() + " belongs to a different equipment");
        }

        UUID defectRepairRequestId = defect.getRepairRequestId();
        if (defectRepairRequestId != null && request.repairRequestId() == null) {
            throw RestException.badRequest(
                    "Defect " + request.defectId() + " belongs to a repair request; repairRequestId is required");
        }
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
            throw RestException
                    .badRequest("warehouseId and replacementEquipmentId are required for replacement work orders");
        }
        return warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(
                workOrder.getWarehouseId(),
                workOrder.getReplacementEquipmentId())
                .orElseThrow(
                        () -> RestException.badRequest("Replacement equipment item not found in selected warehouse"));
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
                    .orElseThrow(() -> RestException
                            .notFound("Replacement equipment not found: " + request.replacementEquipmentId()));
            WarehouseEquipmentItem warehouseEquipmentItem = warehouseEquipmentItemRepository
                    .findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(
                            request.warehouseId(),
                            request.replacementEquipmentId())
                    .orElseThrow(() -> RestException
                            .badRequest("Replacement equipment does not belong to selected warehouse"));
            if (warehouseEquipmentItem.getStatus() != WarehouseEquipmentStatus.AVAILABLE) {
                throw RestException.badRequest("Replacement equipment must be AVAILABLE");
            }
            if (repository.existsActiveReplacementAssignment(
                    request.replacementEquipmentId(),
                    WorkType.REPLACEMENT,
                    TERMINAL_WORK_ORDER_STATUSES)) {
                throw RestException.conflict("Replacement equipment is already assigned to another active work order");
            }
            return;
        }
        if (request.warehouseId() != null || request.replacementEquipmentId() != null) {
            throw RestException
                    .badRequest("warehouseId and replacementEquipmentId must be null when workType is not REPLACEMENT");
        }
    }

    private void reserveReplacementEquipmentOnCreate(WorkOrderRequest request, WorkType effectiveWorkType) {
        if (effectiveWorkType != WorkType.REPLACEMENT) {
            return;
        }
        WarehouseEquipmentItem item = warehouseEquipmentItemRepository
                .findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(
                        request.warehouseId(),
                        request.replacementEquipmentId())
                .orElseThrow(
                        () -> RestException.badRequest("Replacement equipment does not belong to selected warehouse"));
        if (item.getStatus() != WarehouseEquipmentStatus.AVAILABLE) {
            throw RestException.badRequest("Replacement equipment must be AVAILABLE");
        }
        item.setStatus(WarehouseEquipmentStatus.RESERVED);
        warehouseEquipmentItemRepository.save(item);
    }

    private void validateCompleteRequestForReplacement(WorkOrder entity, CompleteWorkOrderRequest request) {
        if (isReplacementWorkOrder(entity)) {
            if (request.oldEquipmentReturnWarehouseId() == null) {
                throw RestException
                        .badRequest("oldEquipmentReturnWarehouseId is required when workType is REPLACEMENT");
            }
            warehouseRepository.findByIdAndIsDeletedFalse(request.oldEquipmentReturnWarehouseId())
                    .orElseThrow(() -> RestException
                            .notFound("Warehouse not found: " + request.oldEquipmentReturnWarehouseId()));
            return;
        }
        if (request.oldEquipmentReturnWarehouseId() != null) {
            throw RestException
                    .badRequest("oldEquipmentReturnWarehouseId must be null when workType is not REPLACEMENT");
        }
    }

    private void assignReplacementEquipmentToWorkOrderDepartment(WorkOrder workOrder) {
        Equipment replacementEquipment = equipmentRepository
                .findByIdAndIsDeletedFalse(workOrder.getReplacementEquipmentId())
                .orElseThrow(() -> RestException
                        .notFound("Replacement equipment not found: " + workOrder.getReplacementEquipmentId()));
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
        Map<UUID, Integer> operationsCountByWorkOrderId = loadOperationsCountMap(entity.getId() == null
                ? List.of()
                : List.of(entity.getId()));
        Map<UUID, Integer> materialsCountByWorkOrderId = loadMaterialsCountMap(entity.getId() == null
                ? List.of()
                : List.of(entity.getId()));
        return toDto(
                entity,
                linkedRepairRequest,
                linkedDefect,
                resolveCount(entity.getId(), operationsCountByWorkOrderId),
                resolveCount(entity.getId(), materialsCountByWorkOrderId));
    }

    private WorkOrderDto toDto(WorkOrder entity,
            RepairRequest linkedRepairRequest,
            Defect linkedDefect,
            int operationsCount,
            int materialsCount) {
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
                entity.getId(), entity.getNumber(), entity.getTitle(), entity.getEquipmentId(),
                entity.getDepartmentId(),
                equipmentName, departmentName,
                entity.getRepairRequestId(), entity.getDefectId(), entity.getPprTaskId(), entity.getContractorId(),
                entity.getStatus(), entity.getType(), entity.getWorkType(), entity.getPriority(),
                entity.getStartPlannedAt(), entity.getEndPlannedAt(), entity.getStartedAt(), entity.getCompletedAt(),
                entity.getSummary(), entity.getResult(), entity.getClosureNotes(),
                entity.getCreatedById(), entity.getApprovedById(),
                entity.getWarehouseId(), entity.getReplacementEquipmentId(), replacementEquipmentName,
                entity.getTasks().stream().map(WorkOrderTaskDto::from).toList(),
                TriadLinkMapper.toRepairRequestBrief(linkedRepairRequest),
                TriadLinkMapper.toDefectBrief(linkedDefect),
                operationsCount,
                materialsCount);
    }

    private List<WorkOrderDto> toDtos(List<WorkOrder> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }

        List<UUID> workOrderIds = entities.stream()
                .map(WorkOrder::getId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<UUID, Integer> operationsCountByWorkOrderId = loadOperationsCountMap(workOrderIds);
        Map<UUID, Integer> materialsCountByWorkOrderId = loadMaterialsCountMap(workOrderIds);

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
                        resolveRepairRequestBrief(entity.getRepairRequestId(), repairRequestById),
                        resolveDefectBrief(entity.getDefectId(), defectById),
                        resolveCount(entity.getId(), operationsCountByWorkOrderId),
                        resolveCount(entity.getId(), materialsCountByWorkOrderId)))
                .toList();
    }

    private Map<UUID, Integer> loadOperationsCountMap(List<UUID> workOrderIds) {
        if (workOrderIds.isEmpty()) {
            return Map.of();
        }
        return workExecutionRepository.countByWorkOrderIds(workOrderIds).stream()
                .collect(Collectors.toMap(
                        projection -> projection.getWorkOrderId(),
                        projection -> safeCount(projection.getCount())));
    }

    private Map<UUID, Integer> loadMaterialsCountMap(List<UUID> workOrderIds) {
        if (workOrderIds.isEmpty()) {
            return Map.of();
        }
        return repairMaterialUsageRepository.countByWorkOrderIds(workOrderIds).stream()
                .collect(Collectors.toMap(
                        projection -> projection.getWorkOrderId(),
                        projection -> safeCount(projection.getCount())));
    }

    private int resolveCount(UUID workOrderId, Map<UUID, Integer> countByWorkOrderId) {
        if (workOrderId == null) {
            return 0;
        }
        return countByWorkOrderId.getOrDefault(workOrderId, 0);
    }

    private int safeCount(long count) {
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    private RepairRequest resolveRepairRequestBrief(UUID repairRequestId, Map<UUID, RepairRequest> repairRequestById) {
        if (repairRequestId == null) {
            return null;
        }
        return repairRequestById.get(repairRequestId);
    }

    private Defect resolveDefectBrief(UUID defectId, Map<UUID, Defect> defectById) {
        if (defectId == null) {
            return null;
        }
        return defectById.get(defectId);
    }

    private Page<WorkOrderDto> toDtoPage(Page<WorkOrder> page) {
        if (page.isEmpty()) {
            return new PageImpl<>(List.of(), page.getPageable(), page.getTotalElements());
        }
        return new PageImpl<>(toDtos(page.getContent()), page.getPageable(), page.getTotalElements());
    }

    private String normalizeSearch(String search) {
        if (search == null) {
            return null;
        }
        String trimmed = search.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
