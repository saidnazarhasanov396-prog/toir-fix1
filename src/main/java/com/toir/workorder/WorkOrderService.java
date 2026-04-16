package com.toir.workorder;

import com.toir.auditlog.AuditAction;
import com.toir.auditlog.AuditLogService;
import com.toir.common.audit.RequestContext;
import com.toir.common.exception.RestException;
import com.toir.common.security.SecurityScope;
import com.toir.workorder.dto.CloseWorkOrderRequest;
import com.toir.workorder.dto.WorkOrderDto;
import com.toir.workorder.dto.WorkOrderRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class WorkOrderService {

    private static final String MODULE = "work-order";
    private static final String ENTITY = "WorkOrder";

    private final WorkOrderRepository repository;
    private final AuditLogService auditLogService;
    private final RequestContext requestContext;
    private final SecurityScope securityScope;

    public WorkOrderService(WorkOrderRepository repository,
                            AuditLogService auditLogService,
                            RequestContext requestContext,
                            SecurityScope securityScope) {
        this.repository = repository;
        this.auditLogService = auditLogService;
        this.requestContext = requestContext;
        this.securityScope = securityScope;
    }

    @Transactional(readOnly = true)
    public List<WorkOrderDto> search(WorkOrderStatus status, UUID departmentId, UUID equipmentId) {
        return repository.search(status, departmentId, equipmentId).stream().map(WorkOrderDto::from).toList();
    }

    @Transactional(readOnly = true)
    public WorkOrderDto findById(UUID id) {
        return WorkOrderDto.from(getOrThrow(id));
    }

    public WorkOrderDto create(WorkOrderRequest request) {
        if (request.equipmentId() == null) {
            throw RestException.badRequest("Equipment is required to create a work order");
        }
        if (repository.existsByNumber(request.number())) {
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
        audit(AuditAction.CREATE, saved.getId(), "Создан наряд " + saved.getNumber());
        return WorkOrderDto.from(saved);
    }

    public WorkOrderDto approve(UUID id, UUID approverId) {
        WorkOrder entity = getOrThrow(id);
        if (entity.getStatus() != WorkOrderStatus.DRAFT && entity.getStatus() != WorkOrderStatus.PLANNED) {
            throw RestException.badRequest("Only DRAFT/PLANNED work orders can be approved");
        }
        entity.setStatus(WorkOrderStatus.APPROVED);
        entity.setApprovedById(approverId);
        audit(AuditAction.APPROVE, entity.getId(), "Утверждён наряд " + entity.getNumber());
        return WorkOrderDto.from(entity);
    }

    public WorkOrderDto start(UUID id) {
        WorkOrder entity = getOrThrow(id);
        entity.setStatus(WorkOrderStatus.IN_PROGRESS);
        entity.setStartedAt(Instant.now());
        audit(AuditAction.UPDATE, entity.getId(), "Начато выполнение наряда " + entity.getNumber());
        return WorkOrderDto.from(entity);
    }

    public WorkOrderDto close(UUID id, CloseWorkOrderRequest request) {
        WorkOrder entity = getOrThrow(id);
        if (request.result() == null || request.result().isBlank()) {
            throw RestException.badRequest("Result is required to close a work order");
        }
        entity.setResult(request.result());
        entity.setClosureNotes(request.closureNotes());
        entity.setStatus(WorkOrderStatus.CLOSED);
        entity.setCompletedAt(Instant.now());
        audit(AuditAction.CLOSE, entity.getId(), "Закрыт наряд " + entity.getNumber());
        return WorkOrderDto.from(entity);
    }

    private WorkOrder getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> RestException.notFound("Work order not found: " + id));
    }

    private void audit(AuditAction action, UUID entityId, String message) {
        UUID userId = securityScope.currentUser() != null
                ? UUID.fromString(securityScope.currentUser().id())
                : null;
        auditLogService.record(userId, MODULE, ENTITY, entityId.toString(), action, message,
                requestContext.getIpAddress(), requestContext.getUserAgent());
    }
}
