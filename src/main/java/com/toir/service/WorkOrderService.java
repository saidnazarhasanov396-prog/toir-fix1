package com.toir.service;

import com.toir.dto.workorder.*;
import com.toir.entity.Department;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.PprTask;
import com.toir.entity.repair.RepairRequest;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.enums.PprTaskStatus;
import com.toir.enums.RequestStatus;
import com.toir.enums.WorkOrderStatus;

import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.util.AuditBuilderService;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkOrderService {

    private static final String MODULE = "work-order";
    private static final String ENTITY = "WorkOrder";

    private final WorkOrderRepository repository;
    private final EquipmentRepository equipmentRepository;
    private final DepartmentRepository departmentRepository;
    private final AuditBuilderService auditBuilderService;
    private final PprTaskRepository pprTaskRepository;
    private final RepairRequestRepository repairRequestRepository;


    @Transactional(readOnly = true)
    public List<WorkOrderDto> search(WorkOrderStatus status, UUID departmentId, UUID equipmentId) {
        return repository.search(status, departmentId, equipmentId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<WorkOrderDto> search(WorkOrderStatus status, UUID departmentId, UUID equipmentId, int page, int pageSize, String search) {
        var pageable = PaginationUtils.pageRequest(page, pageSize);
        return repository.searchPaginated(
                status,
                departmentId,
                equipmentId,
                search,
                pageable
        ).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<WorkOrderDto> mobileFeed(UUID departmentId, UUID equipmentId, String search, int page, int pageSize) {
        return repository.searchMobileFeed(
                departmentId,
                equipmentId,
                search,
                PaginationUtils.pageRequest(page, pageSize)
        ).map(this::toDto);
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
        if (repository.existsByNumberAndIsDeletedFalse(request.number())) {
            throw RestException.conflict("Work order number already exists: " + request.number());
        }
        WorkOrder entity = new WorkOrder();
        entity.setNumber(request.number());
        entity.setTitle(request.title());
        entity.setEquipmentId(request.equipmentId());
        entity.setDepartmentId(request.departmentId());
        entity.setRepairRequestId(request.repairRequestId());
        entity.setPprTaskId(request.pprTaskId());
        entity.setContractorId(request.contractorId());
        entity.setType(request.type());
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
        entity.setStatus(WorkOrderStatus.IN_PROGRESS);
        entity.setStartedAt(Instant.now());

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
        entity.setStatus(WorkOrderStatus.COMPLETED);
        entity.setCompletedAt(Instant.now());

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
        if (request.result() == null || request.result().isBlank()) {
            throw RestException.badRequest("Result is required to close a work order");
        }
        entity.setResult(request.result());
        entity.setClosureNotes(request.closureNotes());
        entity.setStatus(WorkOrderStatus.CLOSED);
        entity.setCompletedAt(Instant.now());
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

    private void completeLinkedPprTask(WorkOrder workOrder) {
        if (workOrder.getPprTaskId() == null) {
            return;
        }
        PprTask task = pprTaskRepository.findByIdAndIsDeletedFalse(workOrder.getPprTaskId())
                .orElseThrow(() -> RestException.notFound("PPR task not found: " + workOrder.getPprTaskId()));
        if (task.getStatus() == PprTaskStatus.COMPLETED || task.getStatus() == PprTaskStatus.CANCELLED) {
            return;
        }
        task.setStatus(PprTaskStatus.COMPLETED);

        PprTask saved = pprTaskRepository.save(task);
        auditBuilderService.log(
                "ppr_task",
                task.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.PPR_TASK,
                "Задача ППР завершена при закрытии наряда " + workOrder.getNumber(),
                task,
                saved
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

    private WorkOrderDto toDto(WorkOrder entity) {
        String equipmentName = equipmentRepository.findById(entity.getEquipmentId())
                .map(Equipment::getName)
                .orElse(null);
        String departmentName = departmentRepository.findById(entity.getDepartmentId())
                .map(Department::getName)
                .orElse(null);
        return new WorkOrderDto(
                entity.getId(), entity.getNumber(), entity.getTitle(), entity.getEquipmentId(), entity.getDepartmentId(),
                equipmentName, departmentName,
                entity.getRepairRequestId(), entity.getPprTaskId(), entity.getContractorId(),
                entity.getStatus(), entity.getType(), entity.getPriority(),
                entity.getStartPlannedAt(), entity.getEndPlannedAt(), entity.getStartedAt(), entity.getCompletedAt(),
                entity.getSummary(), entity.getResult(), entity.getClosureNotes(),
                entity.getCreatedById(), entity.getApprovedById(),
                entity.getTasks().stream().map(WorkOrderTaskDto::from).toList()
        );
    }
}
