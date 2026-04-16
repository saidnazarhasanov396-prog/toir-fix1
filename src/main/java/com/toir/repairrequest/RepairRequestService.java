package com.toir.repairrequest;

import com.toir.auditlog.AuditAction;
import com.toir.auditlog.AuditLogService;
import com.toir.common.audit.RequestContext;
import com.toir.common.exception.RestException;
import com.toir.common.security.SecurityScope;
import com.toir.repairrequest.dto.CloseRequestRequest;
import com.toir.repairrequest.dto.RepairRequestDto;
import com.toir.repairrequest.dto.RepairRequestRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class RepairRequestService {

    private static final String MODULE = "repair-request";
    private static final String ENTITY = "RepairRequest";

    private final RepairRequestRepository repository;
    private final AuditLogService auditLogService;
    private final RequestContext requestContext;
    private final SecurityScope securityScope;

    public RepairRequestService(RepairRequestRepository repository,
                                AuditLogService auditLogService,
                                RequestContext requestContext,
                                SecurityScope securityScope) {
        this.repository = repository;
        this.auditLogService = auditLogService;
        this.requestContext = requestContext;
        this.securityScope = securityScope;
    }

    @Transactional(readOnly = true)
    public List<RepairRequestDto> search(RequestStatus status, UUID departmentId, UUID equipmentId) {
        return repository.search(status, departmentId, equipmentId).stream()
                .map(RepairRequestDto::from).toList();
    }

    @Transactional(readOnly = true)
    public RepairRequestDto findById(UUID id) {
        return RepairRequestDto.from(getOrThrow(id));
    }

    public RepairRequestDto create(RepairRequestRequest request) {
        if (repository.existsByNumber(request.number())) {
            throw RestException.conflict("Request number already exists: " + request.number());
        }
        RepairRequest entity = new RepairRequest();
        entity.setNumber(request.number());
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setEquipmentId(request.equipmentId());
        entity.setDepartmentId(request.departmentId());
        entity.setLocationId(request.locationId());
        entity.setReporterId(request.reporterId());
        if (request.priority() != null) entity.setPriority(request.priority());
        if (request.criticality() != null) entity.setCriticality(request.criticality());
        if (request.source() != null) entity.setSource(request.source());
        entity.setTargetCompletionAt(request.targetCompletionAt());
        RepairRequest saved = repository.save(entity);
        audit(AuditAction.CREATE, saved.getId(), "Создана заявка " + saved.getNumber());
        return RepairRequestDto.from(saved);
    }

    public RepairRequestDto changeStatus(UUID id, RequestStatus newStatus) {
        RepairRequest entity = getOrThrow(id);
        captureReaction(entity, newStatus);
        entity.setStatus(newStatus);
        audit(AuditAction.UPDATE, entity.getId(), "Заявка " + entity.getNumber() + " переведена в " + newStatus);
        return RepairRequestDto.from(entity);
    }

    public RepairRequestDto assign(UUID id, UUID assigneeId) {
        RepairRequest entity = getOrThrow(id);
        if (entity.getStatus() == RequestStatus.CLOSED || entity.getStatus() == RequestStatus.CANCELLED) {
            throw RestException.badRequest("Cannot assign a closed/cancelled request");
        }
        captureReaction(entity, RequestStatus.ASSIGNED);
        entity.setAssignedToId(assigneeId);
        entity.setStatus(RequestStatus.ASSIGNED);
        audit(AuditAction.UPDATE, entity.getId(), "Заявка " + entity.getNumber() + " назначена исполнителю");
        return RepairRequestDto.from(entity);
    }

    public RepairRequestDto reject(UUID id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw RestException.badRequest("Rejection reason is required");
        }
        RepairRequest entity = getOrThrow(id);
        if (entity.getStatus() == RequestStatus.CLOSED || entity.getStatus() == RequestStatus.CANCELLED) {
            throw RestException.badRequest("Cannot reject a closed/cancelled request");
        }
        captureReaction(entity, RequestStatus.REJECTED);
        entity.setStatus(RequestStatus.REJECTED);
        entity.setRejectionReason(reason);
        audit(AuditAction.CANCEL, entity.getId(), "Заявка " + entity.getNumber() + " отклонена: " + reason);
        return RepairRequestDto.from(entity);
    }

    public RepairRequestDto requestClarification(UUID id, String comment) {
        if (comment == null || comment.isBlank()) {
            throw RestException.badRequest("Clarification comment is required");
        }
        RepairRequest entity = getOrThrow(id);
        captureReaction(entity, RequestStatus.NEEDS_CLARIFICATION);
        entity.setStatus(RequestStatus.NEEDS_CLARIFICATION);
        entity.setRejectionReason(comment);
        audit(AuditAction.UPDATE, entity.getId(), "Заявка " + entity.getNumber() + " требует уточнения: " + comment);
        return RepairRequestDto.from(entity);
    }

    private void captureReaction(RepairRequest entity, RequestStatus nextStatus) {
        if (entity.getReactedAt() == null
                && nextStatus != RequestStatus.OPEN
                && nextStatus != RequestStatus.DRAFT
                && nextStatus != RequestStatus.REGISTERED) {
            entity.setReactedAt(Instant.now());
        }
    }

    public RepairRequestDto close(UUID id, CloseRequestRequest request) {
        RepairRequest entity = getOrThrow(id);
        if (request.closeResult() == null || request.closeResult().isBlank()) {
            throw RestException.badRequest("Close result is required");
        }
        entity.setCloseResult(request.closeResult());
        entity.setActualCompletionAt(Instant.now());
        entity.setStatus(RequestStatus.CLOSED);
        audit(AuditAction.CLOSE, entity.getId(), "Закрыта заявка " + entity.getNumber());
        return RepairRequestDto.from(entity);
    }

    private RepairRequest getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> RestException.notFound("Repair request not found: " + id));
    }

    private void audit(AuditAction action, UUID entityId, String message) {
        UUID userId = securityScope.currentUser() != null
                ? UUID.fromString(securityScope.currentUser().id())
                : null;
        auditLogService.record(userId, MODULE, ENTITY, entityId.toString(), action, message,
                requestContext.getIpAddress(), requestContext.getUserAgent());
    }
}
