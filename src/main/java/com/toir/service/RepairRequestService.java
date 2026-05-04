package com.toir.service;
import com.toir.entity.*;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestStatus;
import com.toir.repository.DepartmentRepository;
import com.toir.repository.EquipmentRepository;
import com.toir.repository.LocationRepository;
import com.toir.repository.RepairRequestRepository;
import com.toir.repository.UserRepository;

import com.toir.enums.AuditAction;
import com.toir.util.PaginationUtils;
import com.toir.util.RequestContext;
import com.toir.exception.RestException;
import com.toir.security.SecurityScope;
import com.toir.dto.repairrequest.CloseRequestRequest;
import com.toir.dto.repairrequest.RepairRequestDto;
import com.toir.dto.repairrequest.RepairRequestRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class RepairRequestService {

    private static final String MODULE = "repair-request";
    private static final String ENTITY = "RepairRequest";

    private final RepairRequestRepository repository;
    private final EquipmentRepository equipmentRepository;
    private final DepartmentRepository departmentRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final RequestContext requestContext;
    private final SecurityScope securityScope;


    @Transactional(readOnly = true)
    public Page<RepairRequestDto> search(RequestStatus status, UUID departmentId, UUID equipmentId, PriorityLevel priority, Integer page, Integer pageSize, String search) {
        var pageable = PaginationUtils.pageRequest(page, pageSize);

        String statusStr = (status != null) ? status.name() : null;
        String priorityStr = (priority != null) ? priority.name() : null;
        return repository.searchPaginated(
                statusStr,
                departmentId,
                equipmentId,
                search,
                priorityStr,
                pageable
        ).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public RepairRequestDto findById(UUID id) {
        return toDto(getOrThrow(id));
    }

    public RepairRequestDto create(RepairRequestRequest request) {
        if (repository.existsByNumberAndIsDeletedFalse(request.number())) {
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
        return toDto(saved);
    }

    public RepairRequestDto changeStatus(UUID id, RequestStatus newStatus) {
        RepairRequest entity = getOrThrow(id);
        captureReaction(entity, newStatus);
        entity.setStatus(newStatus);
        audit(AuditAction.UPDATE, entity.getId(), "Заявка " + entity.getNumber() + " переведена в " + newStatus);
        return toDto(entity);
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
        return toDto(entity);
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
        return toDto(entity);
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
        return toDto(entity);
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
        return toDto(entity);
    }

    private RepairRequestDto toDto(RepairRequest r) {
        String equipmentName = equipmentRepository.findByIdAndIsDeletedFalse(r.getEquipmentId())
                .map(Equipment::getName)
                .orElse(null);
        String departmentName = departmentRepository.findByIdAndIsDeletedFalse(r.getDepartmentId())
                .map(Department::getName)
                .orElse(null);
        String locationName = r.getLocationId() == null ? null
                : locationRepository.findByIdAndIsDeletedFalse(r.getLocationId())
                .map(Location::getName)
                .orElse(null);
        String reporterName = userRepository.findByIdAndIsDeletedFalse(r.getReporterId())
                .map(User::getFullName)
                .orElse(null);

        return new RepairRequestDto(
                r.getId(),
                r.getNumber(),
                r.getTitle(),
                r.getDescription(),
                equipmentName,
                departmentName,
                locationName,
                reporterName,
                r.getAssignedToId(),
                r.getPriority(),
                r.getCriticality(),
                r.getStatus(),
                r.getSource(),
                r.getDetectedAt(),
                r.getTargetCompletionAt(),
                r.getActualCompletionAt(),
                r.getReactedAt(),
                r.getRejectionReason(),
                r.getCloseResult()
        );
    }

    private RepairRequest getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Repair request not found: " + id));
    }

    private void audit(AuditAction action, UUID entityId, String message) {
//        UUID userId = securityScope.currentUser() != null
//                ? UUID.fromString(securityScope.currentUser().id())
//                : null;
//        auditLogService.record(userId, MODULE, ENTITY, entityId.toString(), action, message,
//                requestContext.getIpAddress(), requestContext.getUserAgent());
    }
}
