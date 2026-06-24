package com.toir.service.repair;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.meter.MeterReadingDto;
import com.toir.dto.meter.MeterReadingRequest;
import com.toir.dto.repairrequest.RepairRequestMeterReadingBatchRequest;
import com.toir.dto.repairrequest.RepairRequestFilterRequest;
import com.toir.dto.repairrequest.RepairRequestMeterRequirementDto;
import com.toir.dto.repairrequest.RepairRequestStatsResponse;
import com.toir.dto.workorder.CompletionMeterSnapshotRequest;
import com.toir.entity.*;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.entity.equipment.MeterReading;
import com.toir.entity.maintenance.MaintenanceCompletionAnchor;
import com.toir.entity.maintenance.MaintenanceAction;
import com.toir.entity.maintenance.MaintenanceOperation;
import com.toir.entity.maintenance.MaintenanceTemplate;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairRequest;
import com.toir.entity.repair.RepairRequestTemplate;
import com.toir.entity.repair.RepairRequestTemplateAction;
import com.toir.entity.users.EmployeeSpecialisation;
import com.toir.entity.users.User;
import com.toir.dto.triad.DefectBriefDto;
import com.toir.dto.triad.TriadLinkMapper;
import com.toir.dto.triad.WorkOrderBriefDto;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestStatus;
import com.toir.enums.WarrantyHandling;
import com.toir.enums.DefectStatus;
import com.toir.enums.MeterReadingContext;
import com.toir.enums.MeterSource;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.UserStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.repository.MeterReadingRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.LocationRepository;
import com.toir.repository.maintenance.MaintenanceCompletionAnchorRepository;
import com.toir.repository.maintenance.MaintenanceActionRepository;
import com.toir.repository.maintenance.MaintenanceOperationRepository;
import com.toir.repository.maintenance.MaintenanceTemplateRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.repository.repair.RepairRequestStatsProjection;
import com.toir.repository.repair.RepairRequestTemplateActionRepository;
import com.toir.repository.repair.RepairRequestTemplateRepository;
import com.toir.repository.specification.RepairRequestSpecifications;
import com.toir.repository.users.EmployeeSpecialisationRepository;
import com.toir.repository.users.UserRepository;

import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.security.PermissionConstants;
import com.toir.security.ScopeAccessService;
import com.toir.service.MeterService;
import com.toir.service.NotificationService;
import com.toir.service.OperationalIssueLifecycleSyncService;
import com.toir.service.equipment.EquipmentStatusLifecycleService;
import com.toir.service.maintanance.EquipmentMaintenanceEffectiveRule;
import com.toir.service.maintanance.EquipmentMaintenanceEffectiveRuleResolver;
import com.toir.service.maintanance.MaintenanceDueEventService;
import com.toir.util.AuditBuilderService;
import com.toir.util.PaginationUtils;
import com.toir.exception.RestException;
import com.toir.dto.repairrequest.CloseRequestRequest;
import com.toir.dto.repairrequest.RepairRequestActionReferenceDto;
import com.toir.dto.repairrequest.RepairRequestClarificationRequest;
import com.toir.dto.repairrequest.RepairRequestDto;
import com.toir.dto.repairrequest.RepairRequestRequest;
import com.toir.dto.repairrequest.RepairRequestTemplateSummaryDto;
import com.toir.dto.repairrequest.WarrantyDecisionRequest;
import com.toir.dto.repairrequest.WarrantyStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
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
    private final OperationalIssueLifecycleSyncService operationalIssueLifecycleSyncService;
    private final EquipmentStatusLifecycleService equipmentStatusLifecycleService;
    private final MaintenanceTemplateRepository maintenanceTemplateRepository;
    private final MaintenanceOperationRepository maintenanceOperationRepository;
    private final MaintenanceActionRepository maintenanceActionRepository;
    private final RepairRequestTemplateRepository repairRequestTemplateRepository;
    private final RepairRequestTemplateActionRepository repairRequestTemplateActionRepository;
    private final MaintenanceCompletionAnchorRepository maintenanceCompletionAnchorRepository;
    private final EquipmentMaintenanceEffectiveRuleResolver effectiveRuleResolver;
    private final EquipmentMeterRepository equipmentMeterRepository;
    private final MeterReadingRepository meterReadingRepository;
    private final MeterService meterService;
    private final MaintenanceDueEventService maintenanceDueEventService;
    private final ObjectMapper objectMapper;
    private final EmployeeSpecialisationRepository employeeSpecialisationRepository;

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
    private static final Set<WorkOrderStatus> CLOSE_READY_WORK_ORDER_STATUSES = EnumSet.of(
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
    public Page<RepairRequestDto> search(RepairRequestFilterRequest filter, Integer page, Integer pageSize) {
        Page<RepairRequest> resultPage = repository.findAll(
                RepairRequestSpecifications.byFilter(filterOrEmpty(filter)),
                PaginationUtils.pageRequest(
                        page == null ? 0 : page,
                        pageSize == null ? 20 : pageSize,
                        Sort.by(Sort.Direction.DESC, "updatedAt")
                )
        );
        return toDtoPage(resultPage);
    }

    @Transactional(readOnly = true)
    public RepairRequestDto findById(UUID id) {
        return toDtoWithLinks(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public UUID resolveDepartmentIdForCreate(RepairRequestRequest request) {
        if (request.departmentId() != null) {
            return request.departmentId();
        }
        if (request.equipmentId() == null) {
            throw RestException.badRequest("equipmentId is required to resolve department");
        }
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(request.equipmentId())
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + request.equipmentId()));
        UUID equipmentDepartmentId = equipment.getResponsibleDepartmentId() != null
                ? equipment.getResponsibleDepartmentId()
                : equipment.getDepartmentId();
        if (equipmentDepartmentId == null) {
            throw RestException.badRequest(
                    "departmentId is required because selected equipment has no responsible or physical department"
            );
        }
        return equipmentDepartmentId;
    }

    @Transactional
    public RepairRequestDto create(RepairRequestRequest request) {
        if (repository.existsByNumberAndIsDeletedFalse(request.number())) {
            throw RestException.conflict("Request number already exists: " + request.number());
        }
        equipmentStatusLifecycleService.assertOperationallyAllowed(request.equipmentId(), "create repair request");
        UUID effectiveDepartmentId = resolveDepartmentIdForCreate(request);
        List<RepairRequestRequest.InlineDefectRequest> inlineDefects = normalizeInlineDefects(request);
        if (request.defectId() != null && !inlineDefects.isEmpty()) {
            throw RestException.badRequest("Use either defectId or inline defects, not both");
        }
        List<NormalizedTemplateSelection> templateSelections = normalizeTemplateSelections(request);
        Defect defect = getDefectForCreate(request);

        RepairRequest entity = new RepairRequest();
        entity.setNumber(request.number());
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setTemplateId(templateSelections.isEmpty()
                ? null
                : templateSelections.getFirst().template().getId());
        entity.setEquipmentId(request.equipmentId());
        boolean warrantyActive = isWarrantyActive(request.equipmentId());
        entity.setWarrantyActiveAtCreation(warrantyActive);
        entity.setWarrantyHandling(warrantyActive ? null : WarrantyHandling.NO_WARRANTY_ISSUE);
        entity.setDepartmentId(effectiveDepartmentId);
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
        persistTemplateSelections(saved, templateSelections);

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
                "Yangi ta'mirlash arizasi bor, ijrochi biriktirish kerak.",
                NotificationSeverity.INFO,
                "RepairRequest",
                saved.getId().toString()
        );

        return toDtoWithLinks(saved);
    }

    @Transactional
    public RepairRequestDto recordWarrantyDecision(UUID id, WarrantyDecisionRequest request, UUID currentUserId) {
        RepairRequest entity = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Repair request not found: " + id));

        if (request.warrantyHandling() == WarrantyHandling.EMERGENCY_OVERRIDE
                && (request.emergencyReason() == null || request.emergencyReason().isBlank())) {
            throw RestException.badRequest("emergencyReason is required for EMERGENCY_OVERRIDE");
        }

        entity.setWarrantyHandling(request.warrantyHandling());
        entity.setWarrantyDecisionComment(request.warrantyDecisionComment());
        entity.setSupplierContactedAt(request.supplierContactedAt());
        entity.setSupplierResponse(request.supplierResponse());
        entity.setEmergencyReason(request.emergencyReason());
        entity.setWarrantyDecisionAt(Instant.now());
        entity.setWarrantyDecisionByUserId(currentUserId);

        RepairRequest saved = repository.save(entity);

        auditBuilderService.log(
                "repair_request",
                String.valueOf(saved.getId()),
                AuditAction.UPDATE,
                AuditModule.REPAIR_REQUEST,
                "Warranty decision recorded: " + request.warrantyHandling(),
                null,
                saved
        );

        return toDtoWithLinks(saved);
    }

    @Transactional(readOnly = true)
    public WarrantyStatusResponse getWarrantyStatus(UUID id) {
        RepairRequest entity = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Repair request not found: " + id));
        boolean currentlyActive = isWarrantyActive(entity.getEquipmentId());
        return new WarrantyStatusResponse(
                entity.getWarrantyActiveAtCreation(),
                currentlyActive,
                entity.getWarrantyHandling(),
                entity.getWarrantyDecisionComment(),
                entity.getSupplierContactedAt(),
                entity.getSupplierResponse(),
                entity.getEmergencyReason()
        );
    }

    private List<NormalizedTemplateSelection> normalizeTemplateSelections(RepairRequestRequest request) {
        if (request.templateSelections() != null && !request.templateSelections().isEmpty()) {
            return normalizeExplicitTemplateSelections(request.templateSelections());
        }
        List<UUID> templateIds = distinctTemplateIds(request.templateIds());
        if (templateIds.isEmpty() && request.templateId() != null) {
            templateIds = List.of(request.templateId());
        }
        if (templateIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, MaintenanceTemplate> templates = loadAndValidateTemplates(templateIds);
        Map<UUID, List<MaintenanceOperation>> operationsByTemplate = loadOperationsByTemplate(templateIds);
        List<NormalizedTemplateSelection> selections = new ArrayList<>();
        int templateSequence = 1;
        for (UUID templateId : templateIds) {
            MaintenanceTemplate template = templates.get(templateId);
            List<NormalizedActionSelection> actions = new ArrayList<>();
            int actionSequence = 1;
            for (MaintenanceOperation operation : operationsByTemplate.getOrDefault(templateId, List.of())) {
                actions.add(actionFromOperation(templateId, operation, actionSequence++));
            }
            selections.add(new NormalizedTemplateSelection(template, List.copyOf(actions), templateSequence++));
        }
        return List.copyOf(selections);
    }

    private List<NormalizedTemplateSelection> normalizeExplicitTemplateSelections(
            List<RepairRequestRequest.TemplateSelectionRequest> requestSelections
    ) {
        List<RepairRequestRequest.TemplateSelectionRequest> safeSelections = requestSelections.stream()
                .filter(Objects::nonNull)
                .filter(selection -> selection.templateId() != null)
                .toList();
        List<UUID> templateIds = distinctTemplateIds(safeSelections.stream()
                .map(RepairRequestRequest.TemplateSelectionRequest::templateId)
                .toList());
        if (templateIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, MaintenanceTemplate> templates = loadAndValidateTemplates(templateIds);
        Map<UUID, List<MaintenanceOperation>> operationsByTemplate = loadOperationsByTemplate(templateIds);
        Map<UUID, MaintenanceOperation> operationsById = operationsByTemplate.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toMap(MaintenanceOperation::getId, Function.identity(), (left, ignored) -> left));
        Set<UUID> actionIds = safeSelections.stream()
                .flatMap(selection -> selection.actions() == null
                        ? java.util.stream.Stream.empty()
                        : selection.actions().stream())
                .filter(Objects::nonNull)
                .map(RepairRequestRequest.ActionSelectionRequest::actionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, MaintenanceAction> actionsById = loadAndValidateActions(actionIds);
        Set<UUID> specialistIds = safeSelections.stream()
                .flatMap(selection -> selection.actions() == null
                        ? java.util.stream.Stream.empty()
                        : selection.actions().stream())
                .filter(Objects::nonNull)
                .map(RepairRequestRequest.ActionSelectionRequest::specialistId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        validateSpecialists(specialistIds);

        List<NormalizedTemplateSelection> selections = new ArrayList<>();
        int templateSequence = 1;
        for (UUID templateId : templateIds) {
            MaintenanceTemplate template = templates.get(templateId);
            List<RepairRequestRequest.ActionSelectionRequest> requestedActions = safeSelections.stream()
                    .filter(selection -> templateId.equals(selection.templateId()))
                    .flatMap(selection -> selection.actions() == null
                            ? java.util.stream.Stream.empty()
                            : selection.actions().stream())
                    .filter(Objects::nonNull)
                    .toList();
            List<NormalizedActionSelection> normalizedActions = new ArrayList<>();
            int actionSequence = 1;
            for (RepairRequestRequest.ActionSelectionRequest actionRequest : requestedActions) {
                normalizedActions.add(actionFromRequest(
                        templateId,
                        actionRequest,
                        operationsById,
                        actionsById,
                        actionSequence++));
            }
            selections.add(new NormalizedTemplateSelection(template, List.copyOf(normalizedActions), templateSequence++));
        }
        return List.copyOf(selections);
    }

    private List<UUID> distinctTemplateIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<UUID> unique = ids.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new ArrayList<>(unique);
    }

    private Map<UUID, MaintenanceTemplate> loadAndValidateTemplates(List<UUID> templateIds) {
        Map<UUID, MaintenanceTemplate> templates = maintenanceTemplateRepository.findAllByIdInAndIsDeletedFalse(templateIds)
                .stream()
                .collect(Collectors.toMap(MaintenanceTemplate::getId, Function.identity(), (left, ignored) -> left));
        for (UUID templateId : templateIds) {
            MaintenanceTemplate template = templates.get(templateId);
            if (template == null) {
                throw RestException.notFound("Maintenance template not found: " + templateId);
            }
            if (!template.isActive()) {
                throw RestException.badRequest("Maintenance template is inactive: " + templateId);
            }
        }
        return templates;
    }

    private Map<UUID, List<MaintenanceOperation>> loadOperationsByTemplate(List<UUID> templateIds) {
        if (templateIds.isEmpty()) {
            return Map.of();
        }
        return maintenanceOperationRepository.findAllByTemplateIdInAndIsDeletedFalse(templateIds)
                .stream()
                .filter(operation -> operation.getTemplate() != null)
                .sorted(Comparator.comparingInt(MaintenanceOperation::getSequence))
                .collect(Collectors.groupingBy(
                        operation -> operation.getTemplate().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private Map<UUID, MaintenanceAction> loadAndValidateActions(Set<UUID> actionIds) {
        if (actionIds == null || actionIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, MaintenanceAction> actions = maintenanceActionRepository.findAllByIdInAndIsDeletedFalse(actionIds)
                .stream()
                .collect(Collectors.toMap(MaintenanceAction::getId, Function.identity(), (left, ignored) -> left));
        for (UUID actionId : actionIds) {
            MaintenanceAction action = actions.get(actionId);
            if (action == null) {
                throw RestException.notFound("Maintenance action not found: " + actionId);
            }
            if (!action.isActive()) {
                throw RestException.badRequest("Maintenance action is inactive: " + actionId);
            }
        }
        return actions;
    }

    private void validateSpecialists(Set<UUID> specialistIds) {
        if (specialistIds == null || specialistIds.isEmpty()) {
            return;
        }
        Set<UUID> existingIds = userRepository.findAllByIdInAndIsDeletedFalse(specialistIds)
                .stream()
                .map(User::getId)
                .collect(Collectors.toSet());
        for (UUID specialistId : specialistIds) {
            if (!existingIds.contains(specialistId)) {
                throw RestException.notFound("Specialist not found: " + specialistId);
            }
        }
    }

    private NormalizedActionSelection actionFromRequest(
            UUID templateId,
            RepairRequestRequest.ActionSelectionRequest request,
            Map<UUID, MaintenanceOperation> operationsById,
            Map<UUID, MaintenanceAction> actionsById,
            int sequence
    ) {
        MaintenanceOperation operation = null;
        if (request.operationId() != null) {
            operation = operationsById.get(request.operationId());
            if (operation == null) {
                throw RestException.badRequest("Maintenance operation does not belong to selected template: " + request.operationId());
            }
            if (operation.getTemplate() == null || !templateId.equals(operation.getTemplate().getId())) {
                throw RestException.badRequest("Maintenance operation belongs to another template: " + request.operationId());
            }
        }
        MaintenanceAction requestedAction = request.actionId() == null ? null : actionsById.get(request.actionId());
        MaintenanceAction operationAction = operation == null ? null : operation.getAction();
        if (operationAction != null && requestedAction != null && !operationAction.getId().equals(requestedAction.getId())) {
            throw RestException.badRequest("Action does not match maintenance operation: " + request.actionId());
        }
        MaintenanceAction action = operationAction != null ? operationAction : requestedAction;
        UUID actionId = action == null ? null : action.getId();
        UUID specialistId = request.specialistId() != null
                ? request.specialistId()
                : operation == null ? null : operation.getSpecialistId();
        String customName = trimToNull(request.customName());
        String nameSnapshot = firstText(
                customName,
                operation != null ? operation.getName() : null,
                action != null ? action.getName() : null
        );
        if (nameSnapshot == null) {
            throw RestException.badRequest("Action name is required");
        }
        Double durationHours = operation != null
                ? operation.getDurationHours()
                : action == null ? null : action.getDefaultDurationHours();
        String requiredSkill = operation != null
                ? operation.getRequiredSkill()
                : action == null ? null : action.getRequiredSkill();
        return new NormalizedActionSelection(
                templateId,
                operation == null ? null : operation.getId(),
                actionId,
                specialistId,
                request.specialisationId(),
                sequence,
                customName,
                nameSnapshot,
                durationHours,
                requiredSkill
        );
    }

    private NormalizedActionSelection actionFromOperation(UUID templateId, MaintenanceOperation operation, int sequence) {
        MaintenanceAction action = operation.getAction();
        String nameSnapshot = firstText(operation.getName(), action == null ? null : action.getName());
        if (nameSnapshot == null) {
            throw RestException.badRequest("Maintenance operation name is required: " + operation.getId());
        }
        return new NormalizedActionSelection(
                templateId,
                operation.getId(),
                action == null ? null : action.getId(),
                operation.getSpecialistId(),
                null,
                sequence,
                null,
                nameSnapshot,
                operation.getDurationHours(),
                operation.getRequiredSkill()
        );
    }

    private void persistTemplateSelections(RepairRequest repairRequest, List<NormalizedTemplateSelection> selections) {
        if (selections == null || selections.isEmpty()) {
            return;
        }
        List<RepairRequestTemplate> templateRows = new ArrayList<>();
        List<RepairRequestTemplateAction> actionRows = new ArrayList<>();
        int actionSequence = 1;
        for (NormalizedTemplateSelection selection : selections) {
            RepairRequestTemplate templateRow = new RepairRequestTemplate();
            templateRow.setRepairRequest(repairRequest);
            templateRow.setTemplateId(selection.template().getId());
            templateRow.setSequence(selection.sequence());
            templateRows.add(templateRow);

            for (NormalizedActionSelection action : selection.actions()) {
                RepairRequestTemplateAction actionRow = new RepairRequestTemplateAction();
                actionRow.setRepairRequest(repairRequest);
                actionRow.setTemplateId(action.templateId());
                actionRow.setOperationId(action.operationId());
                actionRow.setActionId(action.actionId());
                actionRow.setSpecialistId(action.specialistId());
                actionRow.setSpecialisationId(action.specialisationId());
                actionRow.setSequence(actionSequence++);
                actionRow.setCustomName(action.customName());
                actionRow.setNameSnapshot(action.nameSnapshot());
                actionRow.setDurationHours(action.durationHours());
                actionRow.setRequiredSkill(action.requiredSkill());
                actionRows.add(actionRow);
            }
        }
        repairRequestTemplateRepository.saveAll(templateRows);
        if (!actionRows.isEmpty()) {
            repairRequestTemplateActionRepository.saveAll(actionRows);
        }
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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

    public RepairRequestDto finalizeApprovalFromApprovalRequest(UUID id) {
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

    /**
     * @deprecated Approval decisions must go through ApprovalService. This wrapper remains for tests and
     * compatibility with older internal callers; approval handlers should call
     * {@link #finalizeApprovalFromApprovalRequest(UUID)}.
     */
    @Deprecated(forRemoval = false)
    @Transactional
    public RepairRequestDto approve(UUID id) {
        return finalizeApprovalFromApprovalRequest(id);
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
                "Sizga ushbu qurilma bo'yicha ta'mirlash vazifasi biriktirildi.",
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

    public RepairRequestDto finalizeRejectionFromApprovalRequest(UUID id, String reason) {
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

    /**
     * @deprecated Approval decisions must go through ApprovalService. This wrapper remains for tests and
     * compatibility with older internal callers; approval handlers should call
     * {@link #finalizeRejectionFromApprovalRequest(UUID, String)}.
     */
    @Deprecated(forRemoval = false)
    @Transactional
    public RepairRequestDto reject(UUID id, String reason) {
        return finalizeRejectionFromApprovalRequest(id, reason);
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

    @Transactional(readOnly = true)
    public RepairRequestStatsResponse getStats(RepairRequestFilterRequest filter) {
        List<RepairRequest> requests = repository.findAll(
                RepairRequestSpecifications.byFilter(filterOrEmpty(filter))
        );
        List<UUID> requestIds = requests.stream()
                .map(RepairRequest::getId)
                .filter(Objects::nonNull)
                .toList();
        Set<UUID> requestIdsWithWorkOrders = requestIds.isEmpty()
                ? Set.of()
                : workOrderRepository.findAllByRepairRequestIdInAndIsDeletedFalseOrderByUpdatedAtDesc(requestIds)
                .stream()
                .map(WorkOrder::getRepairRequestId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        long total = requests.size();
        long emergency = requests.stream()
                .filter(request -> request.getPriority() == PriorityLevel.EMERGENCY)
                .count();
        long open = requests.stream()
                .filter(request -> request.getStatus() == RequestStatus.OPEN)
                .count();
        return new RepairRequestStatsResponse(total, emergency, open, requestIdsWithWorkOrders.size());
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

    private RepairRequestFilterRequest filterOrEmpty(RepairRequestFilterRequest filter) {
        if (filter != null) {
            return filter;
        }
        return new RepairRequestFilterRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private long safe(Long value) {
        return value == null ? 0L : value;
    }

    @Transactional(readOnly = true)
    public List<RepairRequestMeterRequirementDto> getMeterRequirements(UUID id) {
        RepairRequest entity = getOrThrow(id);
        List<EquipmentMeter> meters = activeMeters(entity.getEquipmentId());
        Map<UUID, MeterReading> latestByMeterId = latestRepairReadingsByMeter(id);

        return meters.stream()
                .map(meter -> {
                    MeterReading latest = latestByMeterId.get(meter.getId());
                    return new RepairRequestMeterRequirementDto(
                            meter.getId(),
                            meter.getMeterType(),
                            meter.getName(),
                            meter.getUnit(),
                            meter.getCurrentValue(),
                            false,
                            latest != null,
                            latest == null ? null : latest.getValue(),
                            latest == null ? null : latest.getReadAt()
                    );
                })
                .toList();
    }

    @Transactional
    public List<MeterReadingDto> addMeterReadings(UUID id, RepairRequestMeterReadingBatchRequest request) {
        RepairRequest entity = getOrThrow(id);
        if (TERMINAL_REQUEST_STATUSES.contains(entity.getStatus())) {
            throw RestException.badRequest("Cannot add meter readings to terminal repair request from status " + entity.getStatus());
        }
        if (request == null || request.readings() == null || request.readings().isEmpty()) {
            throw RestException.badRequest("At least one meter reading is required");
        }

        List<MeterReadingDto> result = new ArrayList<>();
        for (var reading : request.readings()) {
            EquipmentMeter meter = equipmentMeterRepository.findByIdAndIsDeletedFalse(reading.meterId())
                    .orElseThrow(() -> RestException.notFound("Equipment meter not found: " + reading.meterId()));
            if (!meter.getEquipmentId().equals(entity.getEquipmentId())) {
                throw RestException.badRequest("Meter " + meter.getId() + " does not belong to repair request equipment");
            }
            result.add(meterService.addReading(
                    new MeterReadingRequest(
                            reading.meterId(),
                            reading.value(),
                            reading.readAt(),
                            MeterSource.MANUAL,
                            reading.recordedByUserId(),
                            reading.deviceId(),
                            reading.note()
                    ),
                    MeterReadingContext.FAILURE_DETECTED,
                    id,
                    null,
                    null
            ));
        }
        return List.copyOf(result);
    }

    @Transactional(readOnly = true)
    public void assertMeterReadingsReadyForApproval(UUID id) {
        RepairRequest entity = getOrThrow(id);
        assertCanTransition(
                entity,
                RequestStatus.APPROVED,
                REVIEWABLE_STATUSES,
                "Cannot start approval for repair request from status "
        );
    }

    private List<EquipmentMeter> activeMeters(UUID equipmentId) {
        if (equipmentId == null) {
            return List.of();
        }
        List<EquipmentMeter> meters = equipmentMeterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId);
        return meters == null ? List.of() : meters;
    }

    private Map<UUID, MeterReading> latestRepairReadingsByMeter(UUID repairRequestId) {
        List<MeterReading> readings = meterReadingRepository
                .findAllByRepairRequestIdAndIsDeletedFalseOrderByReadAtDesc(repairRequestId);
        if (readings == null || readings.isEmpty()) {
            return Map.of();
        }
        return readings.stream()
                .filter(reading -> reading.getMeterId() != null)
                .collect(Collectors.toMap(
                        MeterReading::getMeterId,
                        reading -> reading,
                        (first, ignored) -> first
                ));
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
        operationalIssueLifecycleSyncService.sweepRepairRequest(save.getId(), "Repair request closed.");
        createCompletionAnchor(save);

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
        if (linkedWorkOrders.stream().anyMatch(workOrder -> !isWorkOrderCloseReady(workOrder))) {
            throw RestException.badRequest("Cannot close repair request while linked work orders are not closed or cancelled");
        }

        List<Defect> linkedDefects = defectRepository
                .findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(entity.getId());
        if (linkedDefects.stream().anyMatch(defect -> !isDefectTerminal(defect))) {
            throw RestException.badRequest("Cannot close repair request while open linked defects exist");
        }
    }

    private boolean isWorkOrderCloseReady(WorkOrder workOrder) {
        return workOrder != null && CLOSE_READY_WORK_ORDER_STATUSES.contains(workOrder.getStatus());
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
        return toDto(r, linkedDefects, linkedWorkOrders, List.of());
    }

    private RepairRequestDto toDto(RepairRequest r,
                                   List<DefectBriefDto> linkedDefects,
                                   List<WorkOrderBriefDto> linkedWorkOrders,
                                   List<MeterReadingDto> meterReadings) {
        return toDto(r, linkedDefects, linkedWorkOrders, meterReadings, List.of(), List.of());
    }

    private RepairRequestDto toDto(RepairRequest r,
                                   List<DefectBriefDto> linkedDefects,
                                   List<WorkOrderBriefDto> linkedWorkOrders,
                                   List<MeterReadingDto> meterReadings,
                                   List<RepairRequestTemplateSummaryDto> templates,
                                   List<RepairRequestActionReferenceDto> actionReferences) {
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
                r.getTemplateId(),
                templateIdsForDto(r, templates),
                templates,
                actionReferences,
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
                linkedWorkOrders,
                meterReadings,
                r.getWarrantyActiveAtCreation(),
                r.getWarrantyHandling(),
                r.getWarrantyDecisionComment(),
                r.getSupplierContactedAt(),
                r.getSupplierResponse(),
                r.getEmergencyReason()
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
        List<MeterReadingDto> meterReadings = repairMeterReadingDtos(repairRequest.getId());
        List<RepairRequestTemplateSummaryDto> templates = repairRequestTemplates(repairRequest);
        List<RepairRequestActionReferenceDto> actionReferences = repairRequestActionReferences(repairRequest);
        return toDto(repairRequest, linkedDefects, linkedWorkOrders, meterReadings, templates, actionReferences);
    }

    private List<UUID> templateIdsForDto(RepairRequest repairRequest, List<RepairRequestTemplateSummaryDto> templates) {
        if (templates != null && !templates.isEmpty()) {
            return templates.stream().map(RepairRequestTemplateSummaryDto::templateId).toList();
        }
        return repairRequest.getTemplateId() == null ? List.of() : List.of(repairRequest.getTemplateId());
    }

    private List<RepairRequestTemplateSummaryDto> repairRequestTemplates(RepairRequest repairRequest) {
        if (repairRequest.getId() == null) {
            return List.of();
        }
        List<RepairRequestTemplate> rows = repairRequestTemplateRepository
                .findAllByRepairRequest_IdAndIsDeletedFalseOrderBySequenceAsc(repairRequest.getId());
        List<UUID> templateIds = rows.stream()
                .map(RepairRequestTemplate::getTemplateId)
                .filter(Objects::nonNull)
                .toList();
        if (templateIds.isEmpty() && repairRequest.getTemplateId() != null) {
            templateIds = List.of(repairRequest.getTemplateId());
        }
        if (templateIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, MaintenanceTemplate> templatesById = maintenanceTemplateRepository.findAllByIdInAndIsDeletedFalse(templateIds)
                .stream()
                .collect(Collectors.toMap(MaintenanceTemplate::getId, Function.identity(), (left, ignored) -> left));
        if (rows.isEmpty()) {
            MaintenanceTemplate template = templatesById.get(repairRequest.getTemplateId());
            return List.of(new RepairRequestTemplateSummaryDto(
                    repairRequest.getTemplateId(),
                    template == null ? null : template.getCode(),
                    template == null ? null : template.getName(),
                    1
            ));
        }
        return rows.stream()
                .map(row -> {
                    MaintenanceTemplate template = templatesById.get(row.getTemplateId());
                    return new RepairRequestTemplateSummaryDto(
                            row.getTemplateId(),
                            template == null ? null : template.getCode(),
                            template == null ? null : template.getName(),
                            row.getSequence()
                    );
                })
                .toList();
    }

    private List<RepairRequestActionReferenceDto> repairRequestActionReferences(RepairRequest repairRequest) {
        if (repairRequest.getId() == null) {
            return List.of();
        }
        List<RepairRequestTemplateAction> rows = repairRequestTemplateActionRepository
                .findAllByRepairRequest_IdAndIsDeletedFalseOrderBySequenceAsc(repairRequest.getId());
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<UUID> templateIds = rows.stream()
                .map(RepairRequestTemplateAction::getTemplateId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, MaintenanceTemplate> templatesById = templateIds.isEmpty()
                ? Map.of()
                : maintenanceTemplateRepository.findAllByIdInAndIsDeletedFalse(templateIds).stream()
                .collect(Collectors.toMap(MaintenanceTemplate::getId, Function.identity(), (left, ignored) -> left));
        Set<UUID> specialistIds = rows.stream()
                .map(RepairRequestTemplateAction::getSpecialistId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> specialistNames = specialistIds.isEmpty()
                ? Map.of()
                : userRepository.findAllByIdInAndIsDeletedFalse(specialistIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName, (left, ignored) -> left));
        Set<UUID> specialisationIds = rows.stream()
                .map(RepairRequestTemplateAction::getSpecialisationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, EmployeeSpecialisation> specialisationsById = specialisationIds.isEmpty()
                ? Map.of()
                : employeeSpecialisationRepository.findAllByIdInAndIsDeletedFalse(specialisationIds)
                        .stream()
                        .collect(Collectors.toMap(
                                EmployeeSpecialisation::getId,
                                s -> s,
                                (left, ignored) -> left
                        ));
        return rows.stream()
                .map(row -> {
                    MaintenanceTemplate template = templatesById.get(row.getTemplateId());
                    EmployeeSpecialisation spec = row.getSpecialisationId() == null
                            ? null
                            : specialisationsById.get(row.getSpecialisationId());
                    return new RepairRequestActionReferenceDto(
                            row.getTemplateId(),
                            template == null ? null : template.getCode(),
                            template == null ? null : template.getName(),
                            row.getOperationId(),
                            row.getActionId(),
                            row.getSpecialistId(),
                            row.getSpecialistId() == null ? null : specialistNames.get(row.getSpecialistId()),
                            row.getSpecialisationId(),
                            spec == null ? null : spec.getNameRu(),
                            spec == null ? null : spec.getNameEn(),
                            spec == null ? null : spec.getNameUz(),
                            row.getSequence(),
                            row.getNameSnapshot(),
                            row.getDurationHours(),
                            row.getRequiredSkill(),
                            row.getCustomName()
                    );
                })
                .toList();
    }

    private List<MeterReadingDto> repairMeterReadingDtos(UUID repairRequestId) {
        List<MeterReading> readings = meterReadingRepository
                .findAllByRepairRequestIdAndIsDeletedFalseOrderByReadAtDesc(repairRequestId);
        if (readings == null || readings.isEmpty()) {
            return List.of();
        }
        return readings.stream().map(MeterReadingDto::from).toList();
    }

    private void validateMaintenanceTemplate(UUID templateId) {
        if (templateId == null) {
            return;
        }
        maintenanceTemplateRepository.findByIdAndIsDeletedFalse(templateId)
                .orElseThrow(() -> RestException.notFound("Maintenance template not found: " + templateId));
    }

    private void createCompletionAnchor(RepairRequest request) {
        if (request.getId() == null || request.getEquipmentId() == null) {
            return;
        }
        List<EquipmentMaintenanceEffectiveRule> scopes = resolveRepairCompletionScopes(request);
        if (scopes.isEmpty()) {
            return;
        }

        Set<String> existingScopes = maintenanceCompletionAnchorRepository
                .findAllByRepairRequestIdAndIsDeletedFalse(request.getId())
                .stream()
                .map(anchor -> scopeKey(anchor.getRegulationId(), anchor.getEquipmentMaintenanceRuleId()))
                .collect(Collectors.toSet());
        Instant performedAt = request.getActualCompletionAt() == null ? Instant.now() : request.getActualCompletionAt();
        String meterSnapshots = toMeterSnapshotsJson(anchorMeterSnapshots(request, performedAt));

        for (EquipmentMaintenanceEffectiveRule scope : scopes) {
            String scopeKey = scopeKey(scope.regulationId(), scope.equipmentMaintenanceRuleId());
            if (!existingScopes.add(scopeKey)) {
                continue;
            }
            MaintenanceCompletionAnchor anchor = new MaintenanceCompletionAnchor();
            anchor.setEquipmentId(request.getEquipmentId());
            anchor.setRegulationId(scope.regulationId());
            anchor.setEquipmentMaintenanceRuleId(scope.equipmentMaintenanceRuleId());
            anchor.setRepairRequestId(request.getId());
            anchor.setPerformedAt(performedAt);
            anchor.setRecalculationPolicy(scope.recalculationPolicy());
            anchor.setSource("REPAIR_REQUEST");
            anchor.setNote(request.getCloseResult());
            anchor.setMeterSnapshots(meterSnapshots);
            maintenanceCompletionAnchorRepository.save(anchor);
            maintenanceDueEventService.cancelOpenByScopeForReset(
                    request.getEquipmentId(),
                    scope.regulationId(),
                    scope.equipmentMaintenanceRuleId(),
                    "Reset from repair request " + request.getNumber()
            );
        }
    }

    private List<EquipmentMaintenanceEffectiveRule> resolveRepairCompletionScopes(RepairRequest request) {
        if (request.getTemplateId() == null) {
            return List.of();
        }
        return effectiveRuleResolver.resolveApplicable(request.getEquipmentId())
                .stream()
                .filter(rule -> request.getTemplateId().equals(rule.templateId()))
                .filter(rule -> rule.regulationId() != null || rule.equipmentMaintenanceRuleId() != null)
                .toList();
    }

    private List<CompletionMeterSnapshotRequest> currentMeterSnapshots(UUID equipmentId, Instant performedAt) {
        return equipmentMeterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId)
                .stream()
                .map(meter -> toMeterSnapshot(meter, performedAt))
                .toList();
    }

    private List<CompletionMeterSnapshotRequest> anchorMeterSnapshots(RepairRequest request, Instant performedAt) {
        List<CompletionMeterSnapshotRequest> failureSnapshots = repairFailureMeterSnapshots(request.getId(), request.getEquipmentId());
        if (!failureSnapshots.isEmpty()) {
            return failureSnapshots;
        }
        return currentMeterSnapshots(request.getEquipmentId(), performedAt);
    }

    private List<CompletionMeterSnapshotRequest> repairFailureMeterSnapshots(UUID repairRequestId, UUID equipmentId) {
        if (repairRequestId == null || equipmentId == null) {
            return List.of();
        }
        List<MeterReading> readings = meterReadingRepository
                .findAllByRepairRequestIdAndReadingContextAndIsDeletedFalseOrderByReadAtDesc(
                        repairRequestId,
                        MeterReadingContext.FAILURE_DETECTED
                );
        if (readings == null || readings.isEmpty()) {
            return List.of();
        }
        Map<UUID, EquipmentMeter> metersById = equipmentMeterRepository
                .findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId)
                .stream()
                .collect(Collectors.toMap(EquipmentMeter::getId, Function.identity(), (left, ignored) -> left));
        LinkedHashMap<UUID, CompletionMeterSnapshotRequest> latestByMeter = new LinkedHashMap<>();
        for (MeterReading reading : readings) {
            if (reading.getMeterId() == null || latestByMeter.containsKey(reading.getMeterId())) {
                continue;
            }
            EquipmentMeter meter = metersById.get(reading.getMeterId());
            if (meter == null || !equipmentId.equals(reading.getEquipmentId())) {
                continue;
            }
            latestByMeter.put(reading.getMeterId(), new CompletionMeterSnapshotRequest(
                    reading.getMeterId(),
                    meter.getMeterType(),
                    reading.getValue(),
                    reading.getReadAt()
            ));
        }
        return List.copyOf(latestByMeter.values());
    }

    private CompletionMeterSnapshotRequest toMeterSnapshot(EquipmentMeter meter, Instant performedAt) {
        return new CompletionMeterSnapshotRequest(
                meter.getId(),
                meter.getMeterType(),
                meter.getCurrentValue(),
                meter.getLastReadAt() == null ? performedAt : meter.getLastReadAt()
        );
    }

    private String toMeterSnapshotsJson(List<CompletionMeterSnapshotRequest> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(snapshots);
        } catch (JsonProcessingException ex) {
            throw RestException.badRequest("Invalid meter snapshots");
        }
    }

    private String scopeKey(UUID regulationId, UUID equipmentMaintenanceRuleId) {
        return String.valueOf(regulationId) + ":" + String.valueOf(equipmentMaintenanceRuleId);
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
        Map<UUID, List<MeterReadingDto>> readingsByRequestId = meterReadingRepository
                .findAllByRepairRequestIdInAndIsDeletedFalseOrderByReadAtDesc(requestIds)
                .stream()
                .collect(Collectors.groupingBy(
                        MeterReading::getRepairRequestId,
                        Collectors.mapping(MeterReadingDto::from, Collectors.toList())
                ));

        List<RepairRequestDto> dtos = requests.stream()
                .map(request -> toDto(
                        request,
                        defectsByRequestId.getOrDefault(request.getId(), List.of()),
                        workOrdersByRequestId.getOrDefault(request.getId(), List.of()),
                        readingsByRequestId.getOrDefault(request.getId(), List.of())
                ))
                .toList();
        return new PageImpl<>(dtos, page.getPageable(), page.getTotalElements());
    }

    private boolean isWarrantyActive(UUID equipmentId) {
        if (equipmentId == null) {
            return false;
        }
        return equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .map(this::hasActiveWarranty)
                .orElse(false);
    }

    private boolean hasActiveWarranty(Equipment equipment) {
        if (!Boolean.TRUE.equals(equipment.getHasWarranty())) {
            return false;
        }
        LocalDate today = LocalDate.now();
        LocalDate effectiveEnd = equipment.getWarrantyEndDate() != null
                ? equipment.getWarrantyEndDate()
                : equipment.getWarrantyUntil();
        return effectiveEnd == null || !effectiveEnd.isBefore(today);
    }

    private RepairRequest getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Repair request not found: " + id));
    }

    private record NormalizedTemplateSelection(
            MaintenanceTemplate template,
            List<NormalizedActionSelection> actions,
            int sequence
    ) {}

    private record NormalizedActionSelection(
            UUID templateId,
            UUID operationId,
            UUID actionId,
            UUID specialistId,
            UUID specialisationId,
            int sequence,
            String customName,
            String nameSnapshot,
            Double durationHours,
            String requiredSkill
    ) {}
}
