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
import com.toir.enums.DefectStatus;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.UserStatus;
import com.toir.enums.WorkOrderStatus;
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
import com.toir.security.PermissionConstants;
import com.toir.security.ScopeAccessService;
import com.toir.service.NotificationService;
import com.toir.service.equipment.EquipmentStatusLifecycleService;
import com.toir.util.AuditBuilderService;
import com.toir.util.PaginationUtils;
import com.toir.exception.RestException;
import com.toir.dto.repairrequest.CloseRequestRequest;
import com.toir.dto.repairrequest.RepairRequestClarificationRequest;
import com.toir.dto.repairrequest.RepairRequestDto;
import com.toir.dto.repairrequest.RepairRequestRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final ScopeAccessService scopeAccessService;
    private final NotificationService notificationService;
    private final EquipmentStatusLifecycleService equipmentStatusLifecycleService;

    private static final Set<RequestStatus> REVIEWABLE_STATUSES = EnumSet.of(
            RequestStatus.OPEN,
            RequestStatus.REGISTERED,
            RequestStatus.IN_REVIEW,
            RequestStatus.NEEDS_CLARIFICATION
    );
    private static final Set<RequestStatus> TERMINAL_REQUEST_STATUSES = EnumSet.of(
            RequestStatus.REJECTED,
            RequestStatus.CLOSED,
            RequestStatus.CANCELLED
    );
    private static final Set<WorkOrderStatus> TERMINAL_WORK_ORDER_STATUSES = EnumSet.of(
            WorkOrderStatus.COMPLETED,
            WorkOrderStatus.CLOSED,
            WorkOrderStatus.CANCELLED
    );
    private static final Set<DefectStatus> TERMINAL_DEFECT_STATUSES = EnumSet.of(
            DefectStatus.RESOLVED,
            DefectStatus.CLOSED,
            DefectStatus.CANCELLED
    );
    private static final String DEFECT_CODE_PREFIX = "DEF";


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
        equipmentStatusLifecycleService.assertOperationallyAllowed(request.equipmentId(), "create repair request");
        List<RepairRequestRequest.InlineDefectRequest> inlineDefects = normalizeInlineDefects(request);
        if (request.defectId() != null && !inlineDefects.isEmpty()) {
            throw RestException.badRequest("Use either defectId or inline defects, not both");
        }
        Defect defect = getDefectForCreate(request);

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

        if (defect != null) {
            defect.setRepairRequestId(saved.getId());
            defectRepository.save(defect);
        }
        createInlineDefects(saved, inlineDefects);

        auditBuilderService.log(
                "repair_request",
                String.valueOf(saved.getId()),
                AuditAction.CREATE,
                AuditModule.REPAIR_REQUEST,
                "Создана заявка " + saved.getNumber(),
                null,
                saved
        );
        notificationService.notifyDepartmentByPermission(
                saved.getDepartmentId(),
                PermissionConstants.REPAIR_REQUEST_ASSIGN,
                "Repair request created: " + saved.getNumber(),
                "Repair request " + saved.getNumber() + " needs assignment.",
                NotificationSeverity.INFO,
                "RepairRequest",
                saved.getId().toString()
        );

        return toDtoWithLinks(saved);
    }

    private List<RepairRequestRequest.InlineDefectRequest> normalizeInlineDefects(RepairRequestRequest request) {
        List<RepairRequestRequest.InlineDefectRequest> inlineDefects = new ArrayList<>();
        if (hasInlineDefectValue(request.defect())) {
            inlineDefects.add(validateInlineDefect(request.defect()));
        }
        if (request.defects() != null) {
            for (RepairRequestRequest.InlineDefectRequest defect : request.defects()) {
                if (hasInlineDefectValue(defect)) {
                    inlineDefects.add(validateInlineDefect(defect));
                }
            }
        }
        return List.copyOf(inlineDefects);
    }

    private boolean hasInlineDefectValue(RepairRequestRequest.InlineDefectRequest defect) {
        return defect != null
                && (hasText(defect.title())
                || hasText(defect.description())
                || hasText(defect.category())
                || hasText(defect.severity())
                || hasText(defect.failureReason())
                || hasText(defect.rootCause()));
    }

    private RepairRequestRequest.InlineDefectRequest validateInlineDefect(RepairRequestRequest.InlineDefectRequest defect) {
        if (!hasText(defect.title())) {
            throw RestException.badRequest("Defect title is required");
        }
        if (!hasText(defect.description())) {
            throw RestException.badRequest("Defect description is required");
        }
        return defect;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String clean(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private void createInlineDefects(RepairRequest saved, List<RepairRequestRequest.InlineDefectRequest> inlineDefects) {
        if (inlineDefects.isEmpty()) {
            return;
        }

        int year = Year.now().getValue();
        String codePrefix = DEFECT_CODE_PREFIX + "-" + year + "-";
        long sequence = defectRepository.maxSequenceByCodePrefix(codePrefix) + 1;

        for (RepairRequestRequest.InlineDefectRequest request : inlineDefects) {
            Defect defect = new Defect();
            defect.setCode(nextDefectCode(year, sequence));
            while (defectRepository.existsByCode(defect.getCode())) {
                sequence++;
                defect.setCode(nextDefectCode(year, sequence));
            }
            sequence++;
            defect.setTitle(request.title().trim());
            defect.setDescription(request.description().trim());
            defect.setEquipmentId(saved.getEquipmentId());
            defect.setRepairRequestId(saved.getId());
            defect.setCategory(clean(request.category()));
            defect.setSeverity(clean(request.severity()));
            defect.setFailureReason(clean(request.failureReason()));
            defect.setRootCause(clean(request.rootCause()));
            defectRepository.save(defect);
        }
    }

    private String nextDefectCode(int year, long sequence) {
        return "%s-%d-%04d".formatted(DEFECT_CODE_PREFIX, year, sequence);
    }

    private Defect getDefectForCreate(RepairRequestRequest request) {
        if (request.defectId() == null) {
            return null;
        }
        Defect defect = defectRepository.findByIdAndIsDeletedFalse(request.defectId())
                .orElseThrow(() -> RestException.notFound("Defect not found: " + request.defectId()));
        if (isDefectTerminal(defect)) {
            throw RestException.badRequest("Cannot create repair request for terminal defect: " + defect.getStatus());
        }
        if (defect.getRepairRequestId() != null) {
            throw RestException.badRequest("Defect already belongs to a repair request");
        }
        if (!request.equipmentId().equals(defect.getEquipmentId())) {
            throw RestException.badRequest("Defect belongs to a different equipment");
        }
        return defect;
    }

    @Transactional
    public RepairRequestDto changeStatus(UUID id, RequestStatus newStatus) {
        return changeStatus(id, newStatus, null);
    }

    @Transactional
    public RepairRequestDto changeStatus(UUID id, RequestStatus newStatus, String overrideReason) {
        RepairRequest entity = getOrThrow(id);
        assertAdminOverride();
        requireOverrideReason(overrideReason);

        captureReaction(entity, newStatus);
        entity.setStatus(newStatus);

        RepairRequest save = repository.save(entity);

        auditBuilderService.log(
                "repair_request",
                String.valueOf(save.getId()),
                AuditAction.UPDATE,
                AuditModule.REPAIR_REQUEST,
                "Admin override: заявка " + entity.getNumber() + " переведена в " + newStatus
                        + ". Reason: " + overrideReason.trim(),
                entity,
                save
        );
        return toDtoWithLinks(entity);
    }

    @Transactional
    public RepairRequestDto approve(UUID id) {
        RepairRequest entity = getOrThrow(id);
        assertCanTransition(entity, RequestStatus.APPROVED, REVIEWABLE_STATUSES, "Cannot approve repair request from status ");

        captureReaction(entity, RequestStatus.APPROVED);
        entity.setStatus(RequestStatus.APPROVED);
        entity.setRejectionReason(null);
        RepairRequest save = repository.save(entity);

        auditBuilderService.log(
                "repair_request",
                String.valueOf(save.getId()),
                AuditAction.APPROVE,
                AuditModule.REPAIR_REQUEST,
                "Заявка " + entity.getNumber() + " утверждена",
                entity,
                save
        );
        return toDtoWithLinks(entity);
    }

    @Transactional
    public RepairRequestDto assign(UUID id, UUID assigneeId) {
        RepairRequest entity = getOrThrow(id);
        if (assigneeId == null) {
            throw RestException.badRequest("Assignee is required");
        }
        assertCanTransition(
                entity,
                RequestStatus.ASSIGNED,
                Set.of(RequestStatus.APPROVED),
                "Cannot assign repair request from status "
        );
        validateAssignee(assigneeId);

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
        notificationService.notifyUser(
                assigneeId,
                "Repair request assigned: " + entity.getNumber(),
                assignedRepairRequestMessage(entity),
                NotificationSeverity.INFO,
                "RepairRequest",
                entity.getId().toString()
        );
        return toDtoWithLinks(entity);
    }

    private String assignedRepairRequestMessage(RepairRequest entity) {
        String equipmentName = entity.getEquipmentId() == null
                ? "ushbu uskuna"
                : equipmentRepository.findByIdAndIsDeletedFalse(entity.getEquipmentId())
                .map(Equipment::getName)
                .filter(name -> name != null && !name.isBlank())
                .orElse("ushbu uskuna");
        return "Sizga " + entity.getNumber() + " bo'yicha " + equipmentName
                + " uchun texnik ko'rik yoki ta'mirlash vazifasi biriktirildi.";
    }

    private void validateAssignee(UUID assigneeId) {
        User assignee = userRepository.findByIdAndIsDeletedFalse(assigneeId)
                .orElseThrow(() -> RestException.notFound("Assignee not found: " + assigneeId));
        if (assignee.getStatus() != null && assignee.getStatus() != UserStatus.ACTIVE) {
            throw RestException.badRequest("Assignee is inactive: " + assigneeId);
        }
    }

    @Transactional
    public RepairRequestDto reject(UUID id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw RestException.badRequest("Rejection reason is required");
        }
        RepairRequest entity = getOrThrow(id);
        assertCanTransition(entity, RequestStatus.REJECTED, REVIEWABLE_STATUSES, "Cannot reject repair request from status ");

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
        return requestClarification(id, new RepairRequestClarificationRequest(null, comment, null));
    }

    @Transactional
    public RepairRequestDto requestClarification(UUID id, RepairRequestClarificationRequest request) {
        String comment = request == null ? null : request.message();
        if (comment == null || comment.isBlank()) {
            throw RestException.badRequest("Clarification comment is required");
        }
        RepairRequest entity = getOrThrow(id);
        UUID recipientId = request.recipientUserId() != null
                ? request.recipientUserId()
                : entity.getReporterId();
        if (request.recipientUserId() != null
                && userRepository.findByIdAndIsDeletedFalse(request.recipientUserId()).isEmpty()) {
            throw RestException.badRequest("Clarification recipient not found: " + request.recipientUserId());
        }
        assertCanTransition(
                entity,
                RequestStatus.NEEDS_CLARIFICATION,
                REVIEWABLE_STATUSES,
                "Cannot request clarification for repair request from status "
        );
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
        notifyClarificationRequested(entity, recipientId, comment);

        return toDtoWithLinks(entity);
    }

    private void notifyClarificationRequested(RepairRequest entity, UUID recipientId, String comment) {
        if (recipientId == null) {
            throw RestException.badRequest("Clarification recipient is required");
        }
        notificationService.notifyUser(
                recipientId,
                "Clarification requested: " + entity.getNumber(),
                comment,
                NotificationSeverity.INFO,
                "RepairRequest",
                entity.getId().toString()
        );
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
        assertCanClose(entity);

        entity.setCloseResult(request.closeResult());
        entity.setActualCompletionAt(Instant.now());
        entity.setStatus(RequestStatus.CLOSED);

        RepairRequest save = repository.save(entity);

        auditBuilderService.log(
                "repair_request",
                String.valueOf(save.getId()),
                AuditAction.CLOSE,
                AuditModule.REPAIR_REQUEST,
                isAdminOverride()
                        ? "Admin override: закрыта заявка " + entity.getNumber() + ". Reason: " + request.closeResult().trim()
                        : "Закрыта заявка " + entity.getNumber(),
                entity,
                save
        );
        return toDtoWithLinks(entity);
    }

    private void assertCanTransition(
            RepairRequest entity,
            RequestStatus nextStatus,
            Set<RequestStatus> allowedFrom,
            String messagePrefix
    ) {
        if (!allowedFrom.contains(entity.getStatus())) {
            throw RestException.badRequest(messagePrefix + entity.getStatus());
        }
        if (TERMINAL_REQUEST_STATUSES.contains(entity.getStatus())) {
            throw RestException.badRequest(messagePrefix + entity.getStatus());
        }
    }

    private void assertCanClose(RepairRequest entity) {
        if (entity.getStatus() == RequestStatus.CLOSED) {
            throw RestException.badRequest("Cannot close repair request from status " + entity.getStatus());
        }
        if (entity.getStatus() == RequestStatus.REJECTED || entity.getStatus() == RequestStatus.CANCELLED) {
            throw RestException.badRequest("Cannot close terminal repair request from status " + entity.getStatus());
        }
        if (isAdminOverride()) {
            return;
        }
        if (entity.getStatus() != RequestStatus.COMPLETED) {
            throw RestException.badRequest("Cannot close repair request from status " + entity.getStatus());
        }

        List<WorkOrder> linkedWorkOrders = workOrderRepository
                .findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(entity.getId());
        if (linkedWorkOrders.isEmpty()) {
            throw RestException.badRequest("Cannot close repair request without linked work order execution evidence");
        }
        if (linkedWorkOrders.stream().anyMatch(workOrder -> !isWorkOrderTerminal(workOrder))) {
            throw RestException.badRequest("Cannot close repair request while active linked work orders exist");
        }

        List<Defect> linkedDefects = defectRepository
                .findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(entity.getId());
        if (linkedDefects.stream().anyMatch(defect -> !isDefectTerminal(defect))) {
            throw RestException.badRequest("Cannot close repair request while open linked defects exist");
        }
    }

    private boolean isWorkOrderTerminal(WorkOrder workOrder) {
        return workOrder != null && TERMINAL_WORK_ORDER_STATUSES.contains(workOrder.getStatus());
    }

    private boolean isDefectTerminal(Defect defect) {
        return defect != null && TERMINAL_DEFECT_STATUSES.contains(defect.getStatus());
    }

    private boolean isAdminOverride() {
        return scopeAccessService.isScopeAdmin();
    }

    private void assertAdminOverride() {
        if (!isAdminOverride()) {
            throw RestException.forbidden("Only SYSTEM_ADMIN or wildcard can override repair request status");
        }
    }

    private void requireOverrideReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw RestException.badRequest("Override reason is required");
        }
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
