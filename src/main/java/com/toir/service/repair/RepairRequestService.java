package com.toir.service.repair;
import com.toir.dto.repairrequest.RepairRequestStatsResponse;
import com.toir.entity.*;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairRequest;
import com.toir.entity.users.User;
import com.toir.dto.triad.DefectBriefDto;
import com.toir.dto.triad.TriadLinkMapper;
import com.toir.dto.triad.WorkOrderBriefDto;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestStatus;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.LocationRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.repository.repair.RepairRequestStatsProjection;
import com.toir.repository.users.UserRepository;

import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.util.AuditBuilderService;
import com.toir.util.PaginationUtils;
import com.toir.exception.RestException;
import com.toir.dto.repairrequest.CloseRequestRequest;
import com.toir.dto.repairrequest.RepairRequestDto;
import com.toir.dto.repairrequest.RepairRequestRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RepairRequestService {

    private final RepairRequestRepository repository;
    private final EquipmentRepository equipmentRepository;
    private final DepartmentRepository departmentRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final DefectRepository defectRepository;
    private final WorkOrderRepository workOrderRepository;
    private final AuditBuilderService auditBuilderService;


    @Transactional(readOnly = true)
    public Page<RepairRequestDto> search(RequestStatus status, UUID departmentId, UUID equipmentId, PriorityLevel priority, Integer page, Integer pageSize, String search) {
        var pageable = PaginationUtils.pageRequest(page, pageSize);

        String statusStr = (status != null) ? status.name() : null;
        String priorityStr = (priority != null) ? priority.name() : null;
        String normalizedSearch = normalizeSearch(search);
        Page<RepairRequest> resultPage = repository.searchPaginated(
                statusStr,
                departmentId,
                equipmentId,
                normalizedSearch,
                priorityStr,
                pageable
        );
        return toDtoPage(resultPage);
    }

    @Transactional(readOnly = true)
    public RepairRequestDto findById(UUID id) {
        return toDtoWithLinks(getOrThrow(id));
    }

    @Transactional
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

        auditBuilderService.log(
                "repair_request",
                String.valueOf(saved.getId()),
                AuditAction.CREATE,
                AuditModule.REPAIR_REQUEST,
                "Создана заявка " + saved.getNumber(),
                null,
                saved
        );

        return toDtoWithLinks(saved);
    }

    @Transactional
    public RepairRequestDto changeStatus(UUID id, RequestStatus newStatus) {
        RepairRequest entity = getOrThrow(id);

        captureReaction(entity, newStatus);
        entity.setStatus(newStatus);

        RepairRequest save = repository.save(entity);

        auditBuilderService.log(
                "repair_request",
                String.valueOf(save.getId()),
                AuditAction.UPDATE,
                AuditModule.REPAIR_REQUEST,
                "Заявка " + entity.getNumber() + " переведена в " + newStatus,
                entity,
                save
        );
        return toDtoWithLinks(entity);
    }


    @Transactional
    public RepairRequestDto assign(UUID id, UUID assigneeId) {
        RepairRequest entity = getOrThrow(id);
        if (entity.getStatus() == RequestStatus.CLOSED || entity.getStatus() == RequestStatus.CANCELLED) {
            throw RestException.badRequest("Cannot assign a closed/cancelled request");
        }

        captureReaction(entity, RequestStatus.ASSIGNED);
        entity.setAssignedToId(assigneeId);
        entity.setStatus(RequestStatus.ASSIGNED);
        RepairRequest save = repository.save(entity);

        auditBuilderService.log(
                "repair_request",
                String.valueOf(save.getId()),
                AuditAction.UPDATE,
                AuditModule.REPAIR_REQUEST,
                "Заявка " + entity.getNumber() + " назначена исполнителю",
                entity,
                save
        );
        return toDtoWithLinks(entity);
    }

    @Transactional
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
        entity.setClarificationReason(null);
        RepairRequest save = repository.save(entity);

        auditBuilderService.log(
                "repair_request",
                String.valueOf(save.getId()),
                AuditAction.CANCEL,
                AuditModule.REPAIR_REQUEST,
                "Заявка " + entity.getNumber() + " отклонена: " + reason,
                entity,
                save
        );
        return toDtoWithLinks(entity);
    }

    @Transactional
    public RepairRequestDto requestClarification(UUID id, String comment) {
        if (comment == null || comment.isBlank()) {
            throw RestException.badRequest("Clarification comment is required");
        }
        RepairRequest entity = getOrThrow(id);
        captureReaction(entity, RequestStatus.NEEDS_CLARIFICATION);
        entity.setStatus(RequestStatus.NEEDS_CLARIFICATION);
        entity.setClarificationReason(comment);
        entity.setRejectionReason(null);

        RepairRequest save = repository.save(entity);

        auditBuilderService.log(
                "repair_request",
                String.valueOf(save.getId()),
                AuditAction.UPDATE,
                AuditModule.REPAIR_REQUEST,
                "Заявка " + entity.getNumber() + " требует уточнения: " + comment,
                entity,
                save
        );

        return toDtoWithLinks(entity);
    }

    @Transactional(readOnly = true)
    public RepairRequestStatsResponse getStats(
            UUID departmentId,
            UUID equipmentId,
            String search
    ) {
        String searchPattern = toSearchPattern(search);

        RepairRequestStatsProjection stats = repository.getRepairRequestStats(
                departmentId,
                equipmentId,
                searchPattern,
                PriorityLevel.EMERGENCY.name(),
                RequestStatus.OPEN.name()
        );

        return new RepairRequestStatsResponse(
                safe(stats.getTotalRequests()),
                safe(stats.getEmergency()),
                safe(stats.getOpen()),
                safe(stats.getWithWorkOrder())
        );
    }

    private String toSearchPattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }

        return "%" + search.trim().toLowerCase() + "%";
    }

    private String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return search.trim();
    }

    private long safe(Long value) {
        return value == null ? 0L : value;
    }

    private void captureReaction(RepairRequest entity, RequestStatus nextStatus) {
        if (entity.getReactedAt() == null
                && nextStatus != RequestStatus.OPEN
                && nextStatus != RequestStatus.DRAFT
                && nextStatus != RequestStatus.REGISTERED) {
            entity.setReactedAt(Instant.now());
        }
    }

    @Transactional
    public RepairRequestDto close(UUID id, CloseRequestRequest request) {
        RepairRequest entity = getOrThrow(id);
        if (request.closeResult() == null || request.closeResult().isBlank()) {
            throw RestException.badRequest("Close result is required");
        }

        entity.setCloseResult(request.closeResult());
        entity.setActualCompletionAt(Instant.now());
        entity.setStatus(RequestStatus.CLOSED);

        RepairRequest save = repository.save(entity);

        auditBuilderService.log(
                "repair_request",
                String.valueOf(save.getId()),
                AuditAction.CLOSE,
                AuditModule.REPAIR_REQUEST,
                "Закрыта заявка " + entity.getNumber(),
                entity,
                save
        );
        return toDtoWithLinks(entity);
    }

    private RepairRequestDto toDto(RepairRequest r,
                                   List<DefectBriefDto> linkedDefects,
                                   List<WorkOrderBriefDto> linkedWorkOrders) {
        String equipmentName = r.getEquipmentId() == null ? null
                : equipmentRepository.findByIdAndIsDeletedFalse(r.getEquipmentId())
                .map(Equipment::getName)
                .orElse(null);
        String departmentName = r.getDepartmentId() == null ? null
                : departmentRepository.findByIdAndIsDeletedFalse(r.getDepartmentId())
                .map(Department::getName)
                .orElse(null);
        String locationName = r.getLocationId() == null ? null
                : locationRepository.findByIdAndIsDeletedFalse(r.getLocationId())
                .map(Location::getName)
                .orElse(null);
        String reporterName = r.getReporterId() == null ? null
                : userRepository.findByIdAndIsDeletedFalse(r.getReporterId())
                .map(User::getFullName)
                .orElse(null);

        return new RepairRequestDto(
                r.getId(),
                r.getNumber(),
                r.getTitle(),
                r.getDescription(),
                r.getEquipmentId(),
                equipmentName,
                r.getDepartmentId(),
                departmentName,
                locationName,
                r.getReporterId(),
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
                r.getClarificationReason(),
                r.getCloseResult(),
                linkedDefects,
                linkedWorkOrders
        );
    }

    private RepairRequestDto toDtoWithLinks(RepairRequest repairRequest) {
        List<DefectBriefDto> linkedDefects = defectRepository
                .findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(repairRequest.getId())
                .stream()
                .map(TriadLinkMapper::toDefectBrief)
                .toList();
        List<WorkOrderBriefDto> linkedWorkOrders = workOrderRepository
                .findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(repairRequest.getId())
                .stream()
                .map(TriadLinkMapper::toWorkOrderBrief)
                .toList();
        return toDto(repairRequest, linkedDefects, linkedWorkOrders);
    }

    private Page<RepairRequestDto> toDtoPage(Page<RepairRequest> page) {
        if (page.isEmpty()) {
            return new PageImpl<>(List.of(), page.getPageable(), page.getTotalElements());
        }
        List<RepairRequest> requests = page.getContent();
        List<UUID> requestIds = requests.stream().map(RepairRequest::getId).toList();

        Map<UUID, List<DefectBriefDto>> defectsByRequestId = defectRepository
                .findAllByRepairRequestIdInAndIsDeletedFalseOrderByUpdatedAtDesc(requestIds)
                .stream()
                .collect(Collectors.groupingBy(
                        Defect::getRepairRequestId,
                        Collectors.mapping(TriadLinkMapper::toDefectBrief, Collectors.toList())
                ));
        Map<UUID, List<WorkOrderBriefDto>> workOrdersByRequestId = workOrderRepository
                .findAllByRepairRequestIdInAndIsDeletedFalseOrderByUpdatedAtDesc(requestIds)
                .stream()
                .collect(Collectors.groupingBy(
                        WorkOrder::getRepairRequestId,
                        Collectors.mapping(TriadLinkMapper::toWorkOrderBrief, Collectors.toList())
                ));

        List<RepairRequestDto> dtos = requests.stream()
                .map(request -> toDto(
                        request,
                        defectsByRequestId.getOrDefault(request.getId(), List.of()),
                        workOrdersByRequestId.getOrDefault(request.getId(), List.of())
                ))
                .toList();
        return new PageImpl<>(dtos, page.getPageable(), page.getTotalElements());
    }

    private RepairRequest getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Repair request not found: " + id));
    }
}

