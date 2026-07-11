package com.toir.service;

import com.toir.dto.plannedshutdown.*;
import com.toir.dto.workorder.WorkOrderDto;
import com.toir.entity.Department;
import com.toir.entity.PlannedShutdown;
import com.toir.entity.ApprovalRequest;
import com.toir.entity.ApprovalStep;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.plannedshutdown.PlannedShutdownAsset;
import com.toir.entity.plannedshutdown.PlannedShutdownWorkItem;
import com.toir.entity.plannedshutdown.PlannedShutdownReadinessItem;
import com.toir.entity.plannedshutdown.PlannedShutdownIsolationPoint;
import com.toir.entity.users.Employee;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.PlanStatus;
import com.toir.enums.PlannedShutdownAssetDisposition;
import com.toir.enums.PlannedShutdownStatus;
import com.toir.enums.PlannedShutdownWorkItemSourceType;
import com.toir.enums.PlannedShutdownItemStatus;
import com.toir.enums.PlannedShutdownReadinessSeverity;
import com.toir.enums.SafetyPermitStatus;
import com.toir.exception.RestException;
import com.toir.exception.PlannedShutdownBlockerException;
import com.toir.repository.PlannedShutdownRepository;
import com.toir.repository.ApprovalRequestRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownAssetRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownWorkItemRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownReadinessItemRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownIsolationPointRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownStatusHistoryRepository;
import com.toir.repository.SafetyPermitRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.security.ScopeAccessService;
import com.toir.util.AuditBuilderService;
import com.toir.service.plannedshutdown.PlannedShutdownWorkItemPolicy;
import com.toir.service.plannedshutdown.PlannedShutdownReadinessPolicy;
import com.toir.service.plannedshutdown.PlannedShutdownReadinessLifecyclePolicy;
import com.toir.service.plannedshutdown.PlannedShutdownTransitionPolicy;
import com.toir.service.plannedshutdown.PlannedShutdownApprovalScopeHasher;
import com.toir.service.repair.CanonicalWorkSourceResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PlannedShutdownService {

    private final PlannedShutdownRepository repository;
    private final PlannedShutdownAssetRepository assetRepository;
    private final PlannedShutdownWorkItemRepository workItemRepository;
    private final PlannedShutdownReadinessItemRepository readinessItemRepository;
    private final PlannedShutdownIsolationPointRepository isolationPointRepository;
    private final PlannedShutdownStatusHistoryRepository statusHistoryRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final EquipmentRepository equipmentRepository;
    private final DefectRepository defectRepository;
    private final PprTaskRepository pprTaskRepository;
    private final WorkOrderRepository workOrderRepository;
    private final CanonicalWorkSourceResolver canonicalWorkSourceResolver;
    private final WorkOrderService workOrderService;
    private final WorkOrderMaterialReadinessService workOrderMaterialReadinessService;
    private final WorkOrderAssignmentEligibilityService workOrderAssignmentEligibilityService;
    private final SafetyPermitRepository safetyPermitRepository;
    private final PlannedShutdownWorkItemPolicy workItemPolicy;
    private final PlannedShutdownReadinessPolicy readinessPolicy;
    private final PlannedShutdownReadinessLifecyclePolicy readinessLifecyclePolicy;
    private final PlannedShutdownTransitionPolicy transitionPolicy;
    private final PlannedShutdownApprovalScopeHasher approvalScopeHasher;
    private final com.toir.service.plannedshutdown.PlannedShutdownEvidenceService evidenceService;
    private final com.toir.service.plannedshutdown.PlannedShutdownReportService reportService;
    private final ScopeAccessService scopeAccessService;
    private final AuditBuilderService auditBuilderService;

    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z0-9-]{3,64}");
    private static final String ACTIVE_CODE_CONSTRAINT = "uq_planned_shutdowns_active_code";
    private static final Set<PlannedShutdownStatus> SCOPE_MUTABLE_STATUSES = EnumSet.of(
            PlannedShutdownStatus.DRAFT, PlannedShutdownStatus.SCOPE_FORMATION);


    @Transactional(readOnly = true)
    public List<PlannedShutdownDto> findAllFiltered(UUID departmentId, PlannedShutdownStatus status, String search) {
        UUID effectiveDepartmentId = scopeAccessService.enforceDepartmentScope(departmentId);
        if (!scopeAccessService.isScopeAdmin()) {
            scopeAccessService.assertCanAccessDepartment(effectiveDepartmentId);
        }
        String statusStr = status != null ? status.name() : null;
        String searchPattern = (search != null && !search.isBlank()) ? "%" + search.trim().toLowerCase() + "%" : null;
        return repository.findAllFiltered(effectiveDepartmentId, statusStr, searchPattern).stream()
                .map(PlannedShutdownDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<WorkOrderDto> linkedWorkOrders(UUID id) {
        PlannedShutdown shutdown = find(id);
        return workOrderService.findByPlannedShutdown(id, shutdown.getDepartmentId());
    }

    @Transactional
    public PlannedShutdownDetailResponse create(PlannedShutdownCreateRequest r) {
        scopeAccessService.assertCanAccessDepartment(r.departmentId());
        validateWindow(r.startAt(), r.endAt());
        validateOwnership(r.departmentId(), r.responsibleEmployeeId());
        String code = normalizeOrGenerateCode(r.code());
        validateRequestedScope(r.assets());
        validateEquipmentScope(r.departmentId(), r.assets());
        PlannedShutdown s = new PlannedShutdown();
        s.setCode(code);
        s.setName(r.name());
        s.setShutdownType(r.shutdownType().trim().toUpperCase(Locale.ROOT));
        s.setDepartmentId(r.departmentId());
        s.setResponsibleEmployeeId(r.responsibleEmployeeId());
        s.setStartAt(r.startAt());
        s.setEndAt(r.endAt());
        s.setPlannedStartAt(r.startAt());
        s.setPlannedEndAt(r.endAt());
        s.setReason(r.reason());
        s.setObjective(r.objective());
        s.setNotes(r.notes());
        s.setRiskLevel(normalizeNullable(r.riskLevel()));
        s.setRiskScore(r.riskScore());
        s.setStatus(PlannedShutdownStatus.DRAFT);
        s.setScopeVersion(0L);
        s.setWindowVersion(1L);
        PlannedShutdown saved = saveRoot(s, code);
        UUID shutdownId = saved.getId();

        List<PlannedShutdownAsset> initialAssets = r.assets().stream().map(item -> {
            PlannedShutdownAsset asset = new PlannedShutdownAsset();
            asset.setPlannedShutdownId(shutdownId);
            asset.setEquipmentId(item.equipmentId());
            asset.setDisposition(item.disposition());
            asset.setInclusionReason(item.inclusionReason());
            asset.setOrderNumber(item.orderNumber());
            return asset;
        }).toList();
        assetRepository.saveAllAndFlush(initialAssets);
        saved.setScopeVersion(1L);
        saved = saveRoot(saved, code);
        List<PlannedShutdownAssetResponse> assetResponses = initialAssets.stream()
                .map(PlannedShutdownAssetResponse::from).toList();

        auditBuilderService.log(
                "planned_shutdown",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.PLANNED_SHUTDOWN,
                "Плановая остановка создана",
                null,
                saved
        );

        auditBuilderService.log(
                "planned_shutdown_scope", saved.getId().toString(), AuditAction.CREATE,
                AuditModule.PLANNED_SHUTDOWN, "Граница плановой остановки создана",
                new ScopeAuditSnapshot(0L, List.of()), new ScopeAuditSnapshot(1L, assetResponses));

        return PlannedShutdownDetailResponse.from(saved, assetResponses);
    }

    @Transactional(readOnly = true)
    public PlannedShutdownDetailResponse get(UUID id) {
        PlannedShutdown shutdown = find(id);
        return PlannedShutdownDetailResponse.from(shutdown, assetResponses(id), workItemResponses(id));
    }

    @Transactional
    public PlannedShutdownDetailResponse update(UUID id, PlannedShutdownUpdateRequest r) {
        PlannedShutdown shutdown = findLocked(id);
        requireVersion(shutdown, r.version());
        if (!EnumSet.of(PlannedShutdownStatus.DRAFT, PlannedShutdownStatus.SCOPE_FORMATION,
                PlannedShutdownStatus.READINESS_CHECK).contains(shutdown.getLifecycleStatus())) {
            throw RestException.conflict("METADATA_UPDATE_NOT_ALLOWED:" + shutdown.getLifecycleStatus());
        }
        scopeAccessService.assertCanAccessDepartment(r.departmentId());
        validateWindow(r.startAt(), r.endAt());
        validateOwnership(r.departmentId(), r.responsibleEmployeeId());
        String code = normalizeCode(r.code());
        if (repository.existsByCodeAndIdNotAndIsDeletedFalse(code, id)) {
            throw RestException.conflict("Planned shutdown code already exists: " + code);
        }
        validateExistingAssetDepartments(id, r.departmentId());
        PlannedShutdownAuditSnapshot before = PlannedShutdownAuditSnapshot.from(shutdown);
        boolean windowChanged = !Objects.equals(shutdown.getPlannedStartAt(), r.startAt())
                || !Objects.equals(shutdown.getPlannedEndAt(), r.endAt());
        shutdown.setCode(code);
        shutdown.setName(r.name());
        shutdown.setShutdownType(r.shutdownType().trim().toUpperCase(Locale.ROOT));
        shutdown.setDepartmentId(r.departmentId());
        shutdown.setResponsibleEmployeeId(r.responsibleEmployeeId());
        shutdown.setStartAt(r.startAt());
        shutdown.setEndAt(r.endAt());
        shutdown.setPlannedStartAt(r.startAt());
        shutdown.setPlannedEndAt(r.endAt());
        shutdown.setReason(r.reason());
        shutdown.setObjective(r.objective());
        shutdown.setNotes(r.notes());
        shutdown.setRiskLevel(normalizeNullable(r.riskLevel()));
        shutdown.setRiskScore(r.riskScore());
        shutdown.setScopeVersion(shutdown.getScopeVersion() + 1);
        if (windowChanged) bumpWindowVersion(shutdown);
        shutdown.setApprovalScopeVersion(null);
        shutdown.setApprovalScopeHash(null);
        PlannedShutdown saved = saveRoot(shutdown, code);
        auditBuilderService.log("planned_shutdown", id.toString(), AuditAction.UPDATE,
                AuditModule.PLANNED_SHUTDOWN, "Плановая остановка обновлена", before,
                PlannedShutdownAuditSnapshot.from(saved));
        return PlannedShutdownDetailResponse.from(saved, assetResponses(id), workItemResponses(id));
    }

    @Transactional(readOnly = true)
    public PlannedShutdownAssetScopeResponse getAssets(UUID id) {
        PlannedShutdown shutdown = find(id);
        return new PlannedShutdownAssetScopeResponse(id, shutdown.getVersion(), shutdown.getScopeVersion(),
                assetResponses(id));
    }

    @Transactional
    public PlannedShutdownAssetScopeResponse replaceAssets(UUID id, PlannedShutdownAssetReplaceRequest request) {
        PlannedShutdown shutdown = findLocked(id);
        requireVersion(shutdown, request.version());
        if (!SCOPE_MUTABLE_STATUSES.contains(shutdown.getLifecycleStatus())) {
            throw RestException.conflict("Shutdown scope is immutable in status " + shutdown.getLifecycleStatus());
        }
        validateRequestedScope(request.assets());

        validateEquipmentScope(shutdown.getDepartmentId(), request.assets());

        List<PlannedShutdownAsset> existing = assetRepository
                .findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id);
        ScopeAuditSnapshot before = ScopeAuditSnapshot.from(shutdown.getScopeVersion(), existing);
        Map<UUID, PlannedShutdownAsset> existingByEquipment = existing.stream()
                .collect(java.util.stream.Collectors.toMap(PlannedShutdownAsset::getEquipmentId, Function.identity()));
        List<PlannedShutdownAsset> changed = new ArrayList<>();
        for (PlannedShutdownAssetRequest item : request.assets()) {
            PlannedShutdownAsset asset = existingByEquipment.remove(item.equipmentId());
            if (asset == null) {
                asset = new PlannedShutdownAsset();
                asset.setPlannedShutdownId(id);
                asset.setEquipmentId(item.equipmentId());
            }
            asset.setDisposition(item.disposition());
            asset.setInclusionReason(item.inclusionReason());
            asset.setOrderNumber(item.orderNumber());
            changed.add(asset);
        }
        existingByEquipment.values().forEach(asset -> {
            if (workItemRepository.existsByPlannedShutdownIdAndEquipmentIdAndIsDeletedFalse(
                    id, asset.getEquipmentId())) {
                throw RestException.conflict("Cannot remove scope equipment referenced by an active work item: "
                        + asset.getEquipmentId());
            }
            asset.setDeleted(true);
            changed.add(asset);
        });
        assetRepository.saveAllAndFlush(changed);
        PlannedShutdown saved = incrementScope(shutdown);
        List<PlannedShutdownAssetResponse> active = changed.stream().filter(asset -> !asset.isDeleted())
                .sorted(Comparator.comparing(PlannedShutdownAsset::getOrderNumber))
                .map(PlannedShutdownAssetResponse::from).toList();
        ScopeAuditSnapshot after = new ScopeAuditSnapshot(saved.getScopeVersion(), active);
        auditBuilderService.log("planned_shutdown_scope", id.toString(), AuditAction.UPDATE,
                AuditModule.PLANNED_SHUTDOWN, "Граница плановой остановки обновлена", before, after);
        return new PlannedShutdownAssetScopeResponse(id, saved.getVersion(), saved.getScopeVersion(), active);
    }

    @Transactional(readOnly = true)
    public PlannedShutdownWorkItemScopeResponse listWorkItems(UUID id) {
        PlannedShutdown shutdown = find(id);
        return workItemScope(shutdown);
    }

    @Transactional
    public PlannedShutdownWorkItemScopeResponse addWorkItem(UUID id, PlannedShutdownWorkItemRequest request) {
        PlannedShutdown shutdown = findLocked(id);
        requireVersion(shutdown, request.version());
        workItemPolicy.requireScopeMutable(shutdown.getLifecycleStatus());
        workItemPolicy.validate(request);
        requireScopedSource(id, request);
        requireCanonicalAvailable(id, null, request);
        PlannedShutdownWorkItem item = new PlannedShutdownWorkItem();
        item.setPlannedShutdownId(id);
        apply(item, request);
        saveWorkItem(item);
        PlannedShutdown saved = incrementScope(shutdown);
        auditBuilderService.log("planned_shutdown_work_item", item.getId().toString(), AuditAction.CREATE,
                AuditModule.PLANNED_SHUTDOWN, "Источник работ привязан к плановой остановке", null,
                WorkItemAuditSnapshot.from(saved.getScopeVersion(), item));
        return workItemScope(saved);
    }

    @Transactional
    public PlannedShutdownWorkItemScopeResponse updateWorkItem(
            UUID id, UUID itemId, PlannedShutdownWorkItemRequest request) {
        PlannedShutdown shutdown = findLocked(id);
        requireVersion(shutdown, request.version());
        PlannedShutdownWorkItem item = findWorkItem(id, itemId);
        workItemPolicy.validate(request);
        workItemPolicy.requireIdentityMutable(shutdown.getLifecycleStatus(), item.getSourceType(), item.getSourceId(),
                request.sourceType(), request.sourceId());
        requireScopedSource(id, request);
        requireCanonicalAvailable(id, itemId, request);
        WorkItemAuditSnapshot before = WorkItemAuditSnapshot.from(shutdown.getScopeVersion(), item);
        apply(item, request);
        saveWorkItem(item);
        PlannedShutdown saved = incrementScope(shutdown);
        auditBuilderService.log("planned_shutdown_work_item", itemId.toString(), AuditAction.UPDATE,
                AuditModule.PLANNED_SHUTDOWN, "Источник работ плановой остановки обновлён", before,
                WorkItemAuditSnapshot.from(saved.getScopeVersion(), item));
        return workItemScope(saved);
    }

    @Transactional
    public PlannedShutdownWorkItemScopeResponse removeWorkItem(UUID id, UUID itemId, Long version) {
        PlannedShutdown shutdown = findLocked(id);
        requireVersion(shutdown, version);
        workItemPolicy.requireScopeMutable(shutdown.getLifecycleStatus());
        PlannedShutdownWorkItem item = findWorkItem(id, itemId);
        if (workOrderRepository.existsActiveByShutdownWorkItemId(itemId)) {
            throw RestException.conflict("Work item has an active linked Work Order");
        }
        WorkItemAuditSnapshot before = WorkItemAuditSnapshot.from(shutdown.getScopeVersion(), item);
        item.setDeleted(true);
        workItemRepository.saveAndFlush(item);
        PlannedShutdown saved = incrementScope(shutdown);
        auditBuilderService.log("planned_shutdown_work_item", itemId.toString(), AuditAction.DELETE,
                AuditModule.PLANNED_SHUTDOWN, "Источник работ отвязан от плановой остановки", before,
                new WorkItemAuditSnapshot(saved.getScopeVersion(), null));
        return workItemScope(saved);
    }

    @Transactional
    public PlannedShutdownWorkItemScopeResponse reorderWorkItems(
            UUID id, PlannedShutdownWorkItemReorderRequest request) {
        PlannedShutdown shutdown = findLocked(id);
        requireVersion(shutdown, request.version());
        workItemPolicy.requireScopeMutable(shutdown.getLifecycleStatus());
        List<PlannedShutdownWorkItem> items = workItemRepository
                .findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id);
        PlannedShutdownWorkItemAuditSnapshot before = new PlannedShutdownWorkItemAuditSnapshot(
                shutdown.getScopeVersion(), items.stream().map(PlannedShutdownWorkItemResponse::from).toList());
        if (request.itemIds().size() != items.size() || new HashSet<>(request.itemIds()).size() != items.size()) {
            throw RestException.badRequest("Reorder must contain every active work item exactly once");
        }
        Map<UUID, PlannedShutdownWorkItem> byId = items.stream()
                .collect(java.util.stream.Collectors.toMap(PlannedShutdownWorkItem::getId, Function.identity()));
        if (!byId.keySet().equals(new HashSet<>(request.itemIds()))) {
            throw RestException.badRequest("Reorder contains a work item outside this shutdown");
        }
        int temporaryBase = items.stream().mapToInt(PlannedShutdownWorkItem::getOrderNumber).max().orElse(0)
                + items.size() + 1;
        for (int i = 0; i < request.itemIds().size(); i++) {
            byId.get(request.itemIds().get(i)).setOrderNumber(temporaryBase + i);
        }
        workItemRepository.saveAllAndFlush(items);
        for (int i = 0; i < request.itemIds().size(); i++) byId.get(request.itemIds().get(i)).setOrderNumber(i);
        workItemRepository.saveAllAndFlush(items);
        PlannedShutdown saved = incrementScope(shutdown);
        List<PlannedShutdownWorkItemResponse> afterItems = request.itemIds().stream()
                .map(byId::get).map(PlannedShutdownWorkItemResponse::from).toList();
        auditBuilderService.log("planned_shutdown_work_items", id.toString(), AuditAction.UPDATE,
                AuditModule.PLANNED_SHUTDOWN, "Порядок работ плановой остановки изменён", before,
                new PlannedShutdownWorkItemAuditSnapshot(saved.getScopeVersion(), afterItems));
        return workItemScope(saved);
    }

    @Transactional(readOnly = true)
    public PlannedShutdownReadinessScopeResponse listReadiness(UUID id) {
        return readinessScope(find(id));
    }

    @Transactional
    public PlannedShutdownReadinessScopeResponse addReadinessItem(
            UUID id, PlannedShutdownReadinessItemRequest request) {
        PlannedShutdown shutdown = findLocked(id);
        requireVersion(shutdown, request.version());
        requireLifecycle(readinessLifecyclePolicy.canEditReadinessDefinition(shutdown.getLifecycleStatus()),
                "Readiness definitions", shutdown);
        validateReadinessRequest(id, null, request);
        PlannedShutdownReadinessItem item = new PlannedShutdownReadinessItem();
        item.setPlannedShutdownId(id);
        apply(item, request);
        readinessItemRepository.saveAndFlush(item);
        PlannedShutdown saved = incrementScope(shutdown);
        audit("planned_shutdown_readiness", item.getId(), AuditAction.CREATE,
                "Элемент готовности остановки создан", null, PlannedShutdownReadinessItemResponse.from(item));
        return readinessScope(saved);
    }

    @Transactional
    public PlannedShutdownReadinessScopeResponse updateReadinessItem(
            UUID id, UUID itemId, PlannedShutdownReadinessItemRequest request) {
        PlannedShutdown shutdown = findLocked(id);
        requireVersion(shutdown, request.version());
        requireLifecycle(readinessLifecyclePolicy.canEditReadinessDefinition(shutdown.getLifecycleStatus()),
                "Readiness definitions", shutdown);
        PlannedShutdownReadinessItem item = findReadiness(id, itemId);
        if (item.getStatus() == PlannedShutdownItemStatus.PASSED || item.getCompletedAt() != null) {
            throw RestException.conflict("Completed readiness item must be reopened before editing");
        }
        validateReadinessRequest(id, itemId, request);
        var before = PlannedShutdownReadinessItemResponse.from(item);
        apply(item, request);
        readinessItemRepository.saveAndFlush(item);
        PlannedShutdown saved = incrementScope(shutdown);
        audit("planned_shutdown_readiness", itemId, AuditAction.UPDATE,
                "Элемент готовности остановки обновлён", before, PlannedShutdownReadinessItemResponse.from(item));
        return readinessScope(saved);
    }

    @Transactional
    public PlannedShutdownReadinessScopeResponse removeReadinessItem(UUID id, UUID itemId, Long version) {
        PlannedShutdown shutdown = findLocked(id);
        requireVersion(shutdown, version);
        requireLifecycle(readinessLifecyclePolicy.canEditReadinessDefinition(shutdown.getLifecycleStatus()),
                "Readiness definitions", shutdown);
        PlannedShutdownReadinessItem item = findReadiness(id, itemId);
        if (item.getStatus() == PlannedShutdownItemStatus.PASSED || item.getCompletedAt() != null) {
            throw RestException.conflict("Completed readiness item must be reopened before deletion");
        }
        var before = PlannedShutdownReadinessItemResponse.from(item);
        item.setDeleted(true);
        readinessItemRepository.saveAndFlush(item);
        PlannedShutdown saved = incrementScope(shutdown);
        audit("planned_shutdown_readiness", itemId, AuditAction.DELETE,
                "Элемент готовности остановки удалён", before, null);
        return readinessScope(saved);
    }

    @Transactional
    public PlannedShutdownReadinessScopeResponse completeReadinessItem(
            UUID id, UUID itemId, PlannedShutdownReadinessActionRequest request) {
        PlannedShutdown shutdown = findLocked(id);
        requireVersion(shutdown, request.version());
        requireLifecycle(readinessLifecyclePolicy.canActOnReadiness(shutdown.getLifecycleStatus()),
                "Readiness completion", shutdown);
        PlannedShutdownReadinessItem item = findReadiness(id, itemId);
        if (item.getStatus() == PlannedShutdownItemStatus.PASSED) {
            throw RestException.conflict("Readiness item is already completed");
        }
        UUID actor = requireUserActor();
        var before = PlannedShutdownReadinessItemResponse.from(item);
        item.setStatus(PlannedShutdownItemStatus.PASSED);
        item.setEvidence(trimToNull(request.evidence()));
        item.setComment(trimToNull(request.comment()));
        item.setCompletedById(actor);
        item.setCompletedAt(java.time.Instant.now());
        readinessItemRepository.saveAndFlush(item);
        audit("planned_shutdown_readiness", itemId, AuditAction.UPDATE,
                "Готовность подтверждена", before, PlannedShutdownReadinessItemResponse.from(item));
        return readinessScope(shutdown);
    }

    @Transactional
    public PlannedShutdownReadinessScopeResponse reopenReadinessItem(
            UUID id, UUID itemId, PlannedShutdownReadinessActionRequest request) {
        PlannedShutdown shutdown = findLocked(id);
        requireVersion(shutdown, request.version());
        requireLifecycle(readinessLifecyclePolicy.canActOnReadiness(shutdown.getLifecycleStatus()),
                "Readiness reopen", shutdown);
        PlannedShutdownReadinessItem item = findReadiness(id, itemId);
        if (item.getStatus() != PlannedShutdownItemStatus.PASSED || item.getCompletedAt() == null) {
            throw RestException.conflict("Only a completed readiness item can be reopened");
        }
        requireUserActor();
        var before = PlannedShutdownReadinessItemResponse.from(item);
        item.setStatus(PlannedShutdownItemStatus.PENDING);
        item.setCompletedById(null);
        item.setCompletedAt(null);
        item.setEvidence(null);
        item.setComment(trimToNull(request.comment()));
        readinessItemRepository.saveAndFlush(item);
        audit("planned_shutdown_readiness", itemId, AuditAction.UPDATE,
                "Готовность открыта повторно", before, PlannedShutdownReadinessItemResponse.from(item));
        return readinessScope(shutdown);
    }

    @Transactional(readOnly = true)
    public PlannedShutdownIsolationScopeResponse listIsolation(UUID id) {
        return isolationScope(find(id));
    }

    @Transactional
    public PlannedShutdownIsolationScopeResponse addIsolationPoint(
            UUID id, PlannedShutdownIsolationPointRequest request) {
        PlannedShutdown shutdown = findLocked(id);
        requireVersion(shutdown, request.version());
        requireLifecycle(readinessLifecyclePolicy.canEditIsolationDefinition(shutdown.getLifecycleStatus()),
                "Isolation definitions", shutdown);
        validateIsolationRequest(id, null, request);
        PlannedShutdownIsolationPoint point = new PlannedShutdownIsolationPoint();
        point.setPlannedShutdownId(id);
        apply(point, request);
        isolationPointRepository.saveAndFlush(point);
        PlannedShutdown saved = incrementScope(shutdown);
        audit("planned_shutdown_isolation", point.getId(), AuditAction.CREATE,
                "Точка изоляции создана", null, PlannedShutdownIsolationPointResponse.from(point));
        return isolationScope(saved);
    }

    @Transactional
    public PlannedShutdownIsolationScopeResponse updateIsolationPoint(
            UUID id, UUID pointId, PlannedShutdownIsolationPointRequest request) {
        PlannedShutdown shutdown = findLocked(id);
        requireVersion(shutdown, request.version());
        requireLifecycle(readinessLifecyclePolicy.canEditIsolationDefinition(shutdown.getLifecycleStatus()),
                "Isolation definitions", shutdown);
        PlannedShutdownIsolationPoint point = findIsolation(id, pointId);
        if (point.getAppliedAt() != null) throw RestException.conflict("Applied isolation point is immutable");
        validateIsolationRequest(id, pointId, request);
        var before = PlannedShutdownIsolationPointResponse.from(point);
        apply(point, request);
        isolationPointRepository.saveAndFlush(point);
        PlannedShutdown saved = incrementScope(shutdown);
        audit("planned_shutdown_isolation", pointId, AuditAction.UPDATE,
                "Точка изоляции обновлена", before, PlannedShutdownIsolationPointResponse.from(point));
        return isolationScope(saved);
    }

    @Transactional
    public PlannedShutdownIsolationScopeResponse removeIsolationPoint(UUID id, UUID pointId, Long version) {
        PlannedShutdown shutdown = findLocked(id);
        requireVersion(shutdown, version);
        requireLifecycle(readinessLifecyclePolicy.canEditIsolationDefinition(shutdown.getLifecycleStatus()),
                "Isolation definitions", shutdown);
        PlannedShutdownIsolationPoint point = findIsolation(id, pointId);
        if (point.getAppliedAt() != null && point.getReleasedAt() == null) {
            throw RestException.conflict("Applied isolation must be released before deletion");
        }
        var before = PlannedShutdownIsolationPointResponse.from(point);
        point.setDeleted(true);
        isolationPointRepository.saveAndFlush(point);
        PlannedShutdown saved = incrementScope(shutdown);
        audit("planned_shutdown_isolation", pointId, AuditAction.DELETE,
                "Точка изоляции удалена", before, null);
        return isolationScope(saved);
    }

    @Transactional
    public PlannedShutdownIsolationScopeResponse applyIsolation(
            UUID id, UUID pointId, PlannedShutdownIsolationActionRequest request) {
        PlannedShutdown shutdown = findLocked(id);
        requireVersion(shutdown, request.version());
        requireLifecycle(readinessLifecyclePolicy.canApplyIsolation(shutdown.getLifecycleStatus()),
                "Isolation apply", shutdown);
        PlannedShutdownIsolationPoint point = findIsolation(id, pointId);
        if (point.getAppliedAt() != null) throw RestException.conflict("Isolation point is already applied");
        UUID actor = requireEmployeeActor();
        var before = PlannedShutdownIsolationPointResponse.from(point);
        point.setAppliedById(actor);
        point.setAppliedAt(java.time.Instant.now());
        point.setStatus(PlannedShutdownItemStatus.IN_PROGRESS);
        isolationPointRepository.saveAndFlush(point);
        audit("planned_shutdown_isolation", pointId, AuditAction.UPDATE,
                "Изоляция применена", before, PlannedShutdownIsolationPointResponse.from(point));
        return isolationScope(shutdown);
    }

    @Transactional
    public PlannedShutdownIsolationScopeResponse verifyIsolation(
            UUID id, UUID pointId, PlannedShutdownIsolationActionRequest request) {
        PlannedShutdown shutdown = findLocked(id);
        requireVersion(shutdown, request.version());
        requireLifecycle(readinessLifecyclePolicy.canVerifyIsolation(shutdown.getLifecycleStatus()),
                "Isolation verification", shutdown);
        PlannedShutdownIsolationPoint point = findIsolation(id, pointId);
        if (point.getAppliedAt() == null) throw RestException.conflict("Isolation must be applied before verification");
        if (point.getVerifiedAt() != null) throw RestException.conflict("Isolation point is already verified");
        UUID actor = requireEmployeeActor();
        var before = PlannedShutdownIsolationPointResponse.from(point);
        point.setVerifiedById(actor);
        point.setVerifiedAt(java.time.Instant.now());
        point.setStatus(PlannedShutdownItemStatus.PASSED);
        isolationPointRepository.saveAndFlush(point);
        audit("planned_shutdown_isolation", pointId, AuditAction.UPDATE,
                "Изоляция проверена", before, PlannedShutdownIsolationPointResponse.from(point));
        return isolationScope(shutdown);
    }

    @Transactional
    public PlannedShutdownIsolationScopeResponse releaseIsolation(
            UUID id, UUID pointId, PlannedShutdownIsolationActionRequest request) {
        PlannedShutdown shutdown = findLocked(id);
        requireVersion(shutdown, request.version());
        requireLifecycle(readinessLifecyclePolicy.canReleaseIsolation(shutdown.getLifecycleStatus()),
                "Isolation release", shutdown);
        PlannedShutdownIsolationPoint point = findIsolation(id, pointId);
        if (point.getVerifiedAt() == null) throw RestException.conflict("Isolation must be verified before release");
        if (point.getReleasedAt() != null) throw RestException.conflict("Isolation point is already released");
        boolean priorUnreleased = isolationPointRepository
                .findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id).stream()
                .anyMatch(candidate -> candidate.getReleasedAt() == null
                        && candidate.getOrderNumber() < point.getOrderNumber());
        if (priorUnreleased) throw RestException.conflict("ISOLATION_RELEASE_ORDER_VIOLATION");
        UUID actor = requireEmployeeActor();
        var before = PlannedShutdownIsolationPointResponse.from(point);
        point.setReleasedById(actor);
        point.setReleasedAt(java.time.Instant.now());
        isolationPointRepository.saveAndFlush(point);
        audit("planned_shutdown_isolation", pointId, AuditAction.UPDATE,
                "Изоляция снята", before, PlannedShutdownIsolationPointResponse.from(point));
        return isolationScope(shutdown);
    }

    @Transactional(readOnly = true)
    public PlannedShutdownReadinessAssessment assessReadiness(UUID id, java.time.Instant evaluatedAt) {
        return readinessPolicy.evaluateReadiness(readinessFacts(find(id), evaluatedAt));
    }

    @Transactional(readOnly = true)
    public PlannedShutdownReadinessAssessment assessSafeState(UUID id, java.time.Instant evaluatedAt) {
        return readinessPolicy.evaluateSafeState(readinessFacts(find(id), evaluatedAt));
    }

    @Transactional
    public PlannedShutdownDto finalizeApprovalFromApprovalRequest(UUID id, ApprovalRequest approval) {
        PlannedShutdown s = findLocked(id);
        requireCurrentApprovalFacts(s, approval);
        UUID actor = approval.getSteps().stream().filter(step -> step.getDecidedById() != null)
                .max(Comparator.comparingInt(ApprovalStep::getStepNumber)).map(ApprovalStep::getDecidedById)
                .orElseThrow(() -> RestException.conflict("APPROVAL_ACTOR_MISSING"));
        return PlannedShutdownDto.from(executeTransition(s, PlannedShutdownStatus.APPROVED, actor,
                "Approval finalized", approval.getId() == null ? null : "APPROVAL:" + approval.getId(), null));
    }

    @Deprecated(forRemoval = false)
    @Transactional
    public PlannedShutdownDto finalizeApprovalFromApprovalRequest(UUID id) {
        throw RestException.conflict("Canonical approval request evidence is required");
    }

    /**
     * @deprecated Approval decisions must go through ApprovalService. This wrapper remains for tests and
     * compatibility with older internal callers; approval handlers should call
     * {@link #finalizeApprovalFromApprovalRequest(UUID)}.
     */
    @Deprecated(forRemoval = false)
    @Transactional
    public PlannedShutdownDto approve(UUID id) {
        return finalizeApprovalFromApprovalRequest(id);
    }

    @Transactional(readOnly = true)
    public List<PlannedShutdownStatusHistoryResponse> history(UUID id) {
        find(id);
        return statusHistoryRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOccurredAtAsc(id)
                .stream().map(PlannedShutdownStatusHistoryResponse::from).toList();
    }

    @Transactional
    public PlannedShutdownDetailResponse formScope(UUID id, PlannedShutdownTransitionRequest request) {
        return transition(id, request, PlannedShutdownStatus.SCOPE_FORMATION, null);
    }

    @Transactional
    public PlannedShutdownDetailResponse beginReadiness(UUID id, PlannedShutdownTransitionRequest request) {
        return transition(id, request, PlannedShutdownStatus.READINESS_CHECK, null);
    }

    @Transactional
    public PlannedShutdownDetailResponse requestApproval(UUID id, PlannedShutdownTransitionRequest request) {
        PlannedShutdown shutdown = findLocked(id);
        requireVersion(shutdown, request.version());
        requireAllowedTransition(shutdown, PlannedShutdownStatus.PENDING_APPROVAL);
        PlannedShutdownReadinessAssessment assessment = assessReadinessFacts(shutdown, Instant.now());
        List<PlannedShutdownBlocker> submissionBlockers = assessment.blockers().stream()
                .filter(blocker -> !blocker.code().startsWith("APPROVAL_"))
                .filter(blocker -> !blocker.code().equals("WINDOW_OUTSIDE_APPROVED"))
                .toList();
        if (!submissionBlockers.isEmpty()) {
            String codes = submissionBlockers.stream().map(PlannedShutdownBlocker::code).distinct().sorted()
                    .collect(java.util.stream.Collectors.joining(","));
            throw new PlannedShutdownBlockerException(org.springframework.http.HttpStatus.CONFLICT,
                    "APPROVAL_REQUEST_BLOCKED:" + codes, shutdown.getVersion(), submissionBlockers);
        }
        shutdown.setApprovedStartAt(shutdown.getPlannedStartAt());
        shutdown.setApprovedEndAt(shutdown.getPlannedEndAt());
        shutdown.setApprovalScopeVersion(shutdown.getScopeVersion());
        shutdown.setApprovalScopeHash(computeApprovalScopeHash(shutdown));
        return detail(executeTransition(shutdown, PlannedShutdownStatus.PENDING_APPROVAL, requireUserActor(),
                request.reason(), request.correlationKey(), null));
    }

    @Transactional
    public PlannedShutdownDetailResponse prepare(UUID id, PlannedShutdownTransitionRequest request) {
        PlannedShutdown shutdown = findLocked(id);
        requireVersion(shutdown, request.version());
        requireAllowedTransition(shutdown, PlannedShutdownStatus.PREPARATION);
        ApprovalEvidence evidence = currentApprovalEvidence(shutdown);
        if (!evidence.currentScope()) throw blocker(shutdown, "APPROVAL_SCOPE_STALE");
        if (!evidence.productionApproved()) throw blocker(shutdown, "APPROVAL_PRODUCTION_MISSING");
        if (!evidence.hseApproved()) throw blocker(shutdown, "APPROVAL_HSE_MISSING");
        if (Objects.equals(evidence.productionActor(), evidence.hseActor())) {
            throw blocker(shutdown, "APPROVAL_SEPARATION_OF_DUTY_REQUIRED");
        }
        return detail(executeTransition(shutdown, PlannedShutdownStatus.PREPARATION, requireUserActor(),
                request.reason(), request.correlationKey(), null));
    }

    @Transactional
    public PlannedShutdownDetailResponse startShutdown(UUID id, PlannedShutdownTransitionRequest request) {
        PlannedShutdown shutdown = findLocked(id);
        requireVersion(shutdown, request.version());
        requireAllowedTransition(shutdown, PlannedShutdownStatus.SHUTDOWN_STARTED);
        requireProceed(assessReadinessFacts(shutdown, Instant.now()), "SHUTDOWN_START_BLOCKED",
                shutdown.getVersion());
        return detail(executeTransition(shutdown, PlannedShutdownStatus.SHUTDOWN_STARTED, requireUserActor(),
                request.reason(), request.correlationKey(), null));
    }

    @Transactional
    public PlannedShutdownDetailResponse confirmSafeState(UUID id, PlannedShutdownTransitionRequest request) {
        PlannedShutdown shutdown = findLocked(id);
        requireVersion(shutdown, request.version());
        requireAllowedTransition(shutdown, PlannedShutdownStatus.SAFE_STATE);
        requireProceed(readinessPolicy.evaluateSafeState(readinessFacts(shutdown, Instant.now())),
                "SAFE_STATE_BLOCKED", shutdown.getVersion());
        return detail(executeTransition(shutdown, PlannedShutdownStatus.SAFE_STATE, requireUserActor(),
                request.reason(), request.correlationKey(), null));
    }

    @Transactional public PlannedShutdownDetailResponse startRepair(UUID id, PlannedShutdownTransitionRequest r) {
        PlannedShutdown shutdown = findLocked(id);
        requireVersion(shutdown, r.version());
        requireAllowedTransition(shutdown, PlannedShutdownStatus.REPAIR_IN_PROGRESS);
        requireProceed(readinessPolicy.evaluateSafeState(readinessFacts(shutdown, Instant.now())),
                "REPAIR_START_BLOCKED", shutdown.getVersion());
        return detail(executeTransition(shutdown, PlannedShutdownStatus.REPAIR_IN_PROGRESS, requireUserActor(),
                r.reason(), r.correlationKey(), null));
    }
    @Transactional public PlannedShutdownDetailResponse startTesting(UUID id, PlannedShutdownTransitionRequest r) {
        PlannedShutdown shutdown = findLocked(id); requireVersion(shutdown, r.version());
        requireAllowedTransition(shutdown, PlannedShutdownStatus.TESTING);
        requireNoActiveWorkOrders(shutdown, "TESTING_ACTIVE_WORK_ORDERS");
        return detail(executeTransition(shutdown, PlannedShutdownStatus.TESTING, requireUserActor(),
                r.reason(), r.correlationKey(), null));
    }
    @Transactional public PlannedShutdownDetailResponse startStartup(UUID id, PlannedShutdownTransitionRequest r) {
        PlannedShutdown shutdown = findLocked(id); requireVersion(shutdown, r.version());
        requireAllowedTransition(shutdown, PlannedShutdownStatus.STARTUP);
        requireEvidence(shutdown, evidenceService.startupBlockers(id));
        return detail(executeTransition(shutdown, PlannedShutdownStatus.STARTUP, requireUserActor(),
                r.reason(), r.correlationKey(), null));
    }
    @Transactional public PlannedShutdownDetailResponse complete(UUID id, PlannedShutdownTransitionRequest r) {
        PlannedShutdown shutdown = findLocked(id); requireVersion(shutdown, r.version());
        requireAllowedTransition(shutdown, PlannedShutdownStatus.COMPLETED);
        requireNoActiveWorkOrders(shutdown, "COMPLETION_ACTIVE_WORK_ORDERS");
        requireAllIsolationReleased(shutdown, "COMPLETION_ISOLATION_UNRELEASED");
        requireEvidence(shutdown, evidenceService.startupBlockers(id));
        requireEvidence(shutdown, evidenceService.productionReturnBlockers(
                id, shutdown.getScopeVersion(), shutdown.getWindowVersion()));
        return detail(executeTransition(shutdown, PlannedShutdownStatus.COMPLETED, requireUserActor(),
                r.reason(), r.correlationKey(), null));
    }
    @Transactional public PlannedShutdownDetailResponse close(UUID id, PlannedShutdownTransitionRequest r) {
        PlannedShutdown shutdown = findLocked(id); requireVersion(shutdown, r.version());
        requireAllowedTransition(shutdown, PlannedShutdownStatus.CLOSED);
        requireNoActiveWorkOrders(shutdown, "CLOSE_ACTIVE_WORK_ORDERS");
        requireAllIsolationReleased(shutdown, "CLOSE_ISOLATION_UNRELEASED");
        requireEvidence(shutdown, evidenceService.startupBlockers(id));
        requireEvidence(shutdown, evidenceService.productionReturnBlockers(
                id, shutdown.getScopeVersion(), shutdown.getWindowVersion()));
        requireEvidence(shutdown, reportService.closureBlockers(id));
        UUID actor = requireUserActor();
        reportService.createSnapshot(shutdown, actor);
        shutdown.setClosureVersion(shutdown.getClosureVersion() + 1);
        return detail(executeTransition(shutdown, PlannedShutdownStatus.CLOSED, actor,
                r.reason(), r.correlationKey(), null));
    }

    @Transactional(readOnly = true)
    public List<PlannedShutdownStartupTestResponse> startupTests(UUID id) {
        find(id);
        return evidenceService.tests(id);
    }

    @Transactional
    public PlannedShutdownStartupTestResponse createStartupTest(UUID id, PlannedShutdownStartupTestRequest request) {
        PlannedShutdown shutdown = findLocked(id);
        requireVersion(shutdown, request.version());
        return evidenceService.createTest(id, shutdown.getLifecycleStatus(), request);
    }

    @Transactional
    public PlannedShutdownStartupTestResponse recordStartupTestResult(UUID id, UUID testId,
            PlannedShutdownStartupTestResultRequest request) {
        PlannedShutdown shutdown = findLocked(id);
        requireVersion(shutdown, request.version());
        return evidenceService.recordResult(id, testId, shutdown.getLifecycleStatus(), request, requireEmployeeActor());
    }

    @Transactional
    public PlannedShutdownProductionReturnResponse approveProductionReturn(UUID id,
            PlannedShutdownProductionReturnRequest request) {
        PlannedShutdown shutdown = findLocked(id);
        requireVersion(shutdown, request.version());
        evidenceService.requireStartupReady(id);
        return evidenceService.approveProductionReturn(id, shutdown.getLifecycleStatus(), shutdown.getScopeVersion(),
                shutdown.getWindowVersion(), request, requireUserActor());
    }

    @Transactional(readOnly = true)
    public PlannedShutdownProductionReturnResponse productionReturn(UUID id) {
        find(id);
        PlannedShutdown shutdown = find(id);
        return evidenceService.productionReturn(id, shutdown.getScopeVersion(), shutdown.getWindowVersion());
    }

    @Transactional(readOnly = true)
    public PlannedShutdownClosureReport closureReport(UUID id) {
        find(id);
        return reportService.readSnapshot(id);
    }

    @Transactional
    public PlannedShutdownDetailResponse cancel(UUID id, PlannedShutdownTransitionRequest request) {
        PlannedShutdown shutdown = findLocked(id);
        requireVersion(shutdown, request.version());
        if (!transitionPolicy.canCancel(shutdown.getLifecycleStatus())) {
            throw blocker(shutdown, "CANCEL_NOT_ALLOWED");
        }
        requireNoActiveWorkOrders(shutdown, "CANCEL_ACTIVE_WORK_ORDERS");
        return detail(executeTransition(shutdown, PlannedShutdownStatus.CANCELLED, requireUserActor(),
                requireReason(request.reason(), "CANCEL_REASON_REQUIRED", shutdown), request.correlationKey(), null));
    }

    @Transactional
    public PlannedShutdownDetailResponse reschedule(UUID id, PlannedShutdownRescheduleRequest request) {
        PlannedShutdown shutdown = findLocked(id);
        requireVersion(shutdown, request.version());
        if (!transitionPolicy.canReschedule(shutdown.getLifecycleStatus())) {
            throw blocker(shutdown, "RESCHEDULE_NOT_ALLOWED");
        }
        String reason = requireReason(request.reason(), "RESCHEDULE_REASON_REQUIRED", shutdown);
        validateWindow(request.newStartAt(), request.newEndAt());
        Window old = window(shutdown);
        shutdown.setStartAt(request.newStartAt());
        shutdown.setEndAt(request.newEndAt());
        shutdown.setPlannedStartAt(request.newStartAt());
        shutdown.setPlannedEndAt(request.newEndAt());
        shutdown.setApprovedStartAt(null);
        shutdown.setApprovedEndAt(null);
        shutdown.setEffectiveExtensionEndAt(null);
        shutdown.setApprovalScopeVersion(null);
        shutdown.setApprovalScopeHash(null);
        shutdown.setRescheduleReason(reason);
        shutdown.setScopeVersion(shutdown.getScopeVersion() + 1);
        bumpWindowVersion(shutdown);
        PlannedShutdownStatus from = shutdown.getLifecycleStatus();
        PlannedShutdownStatus target = EnumSet.of(PlannedShutdownStatus.DRAFT, PlannedShutdownStatus.SCOPE_FORMATION)
                .contains(shutdown.getLifecycleStatus()) ? PlannedShutdownStatus.SCOPE_FORMATION
                : PlannedShutdownStatus.READINESS_CHECK;
        UUID actor = requireUserActor(); Instant now = Instant.now(); Window changed = window(shutdown);
        persistHistory(shutdown, from, PlannedShutdownStatus.RESCHEDULED, actor, reason,
                eventCorrelation(request.correlationKey(), "RESCHEDULED"), old, changed, now);
        shutdown.setStatus(target); PlannedShutdown saved = repository.saveAndFlush(shutdown);
        persistHistory(saved, PlannedShutdownStatus.RESCHEDULED, target, actor, reason,
                eventCorrelation(request.correlationKey(), "RETURN"), changed, changed, now);
        auditBuilderService.log("planned_shutdown", id.toString(), AuditAction.UPDATE, AuditModule.PLANNED_SHUTDOWN,
                "Shutdown rescheduled", old, changed);
        return detail(saved);
    }

    @Transactional
    public PlannedShutdownDetailResponse extend(UUID id, PlannedShutdownExtensionRequest request) {
        PlannedShutdown shutdown = findLocked(id);
        requireVersion(shutdown, request.version());
        if (!transitionPolicy.canExtend(shutdown.getLifecycleStatus())) {
            throw blocker(shutdown, "EXTENSION_NOT_ALLOWED");
        }
        String reason = requireReason(request.reason(), "EXTENSION_REASON_REQUIRED", shutdown);
        Instant currentEnd = effectiveEnd(shutdown);
        if (currentEnd == null || request.newEndAt() == null || !request.newEndAt().isAfter(currentEnd)) {
            throw blocker(org.springframework.http.HttpStatus.BAD_REQUEST, shutdown,
                    "EXTENSION_END_MUST_INCREASE");
        }
        Window old = window(shutdown); PlannedShutdownStatus current = shutdown.getLifecycleStatus();
        shutdown.setEffectiveExtensionEndAt(request.newEndAt());
        bumpWindowVersion(shutdown);
        shutdown.setExtensionReason(reason);
        UUID actor = requireUserActor(); Instant now = Instant.now(); PlannedShutdown saved = repository.saveAndFlush(shutdown);
        Window changed = window(saved);
        persistHistory(saved, current, PlannedShutdownStatus.EMERGENCY_EXTENDED, actor, reason,
                eventCorrelation(request.correlationKey(), "EXTENDED"), old, changed, now);
        persistHistory(saved, PlannedShutdownStatus.EMERGENCY_EXTENDED, current, actor, reason,
                eventCorrelation(request.correlationKey(), "RETURN"), changed, changed, now);
        auditBuilderService.log("planned_shutdown", id.toString(), AuditAction.UPDATE, AuditModule.PLANNED_SHUTDOWN,
                "Shutdown emergency extension", old, changed);
        return detail(saved);
    }

    private PlannedShutdown find(UUID id) {
        PlannedShutdown shutdown = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Planned shutdown not found: " + id));
        scopeAccessService.assertCanAccessDepartment(shutdown.getDepartmentId());
        return shutdown;
    }

    private PlannedShutdownReadinessScopeResponse readinessScope(PlannedShutdown shutdown) {
        List<PlannedShutdownReadinessItemResponse> items = readinessItemRepository
                .findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(shutdown.getId()).stream()
                .map(PlannedShutdownReadinessItemResponse::from).toList();
        return new PlannedShutdownReadinessScopeResponse(shutdown.getId(), shutdown.getVersion(),
                shutdown.getScopeVersion(), items);
    }

    private PlannedShutdownIsolationScopeResponse isolationScope(PlannedShutdown shutdown) {
        List<PlannedShutdownIsolationPointResponse> points = isolationPointRepository
                .findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(shutdown.getId()).stream()
                .map(PlannedShutdownIsolationPointResponse::from).toList();
        return new PlannedShutdownIsolationScopeResponse(shutdown.getId(), shutdown.getVersion(),
                shutdown.getScopeVersion(), points);
    }

    private PlannedShutdownReadinessItem findReadiness(UUID shutdownId, UUID itemId) {
        return readinessItemRepository.findByIdAndPlannedShutdownIdAndIsDeletedFalse(itemId, shutdownId)
                .orElseThrow(() -> RestException.notFound("Shutdown readiness item not found: " + itemId));
    }

    private PlannedShutdownIsolationPoint findIsolation(UUID shutdownId, UUID pointId) {
        return isolationPointRepository.findByIdAndPlannedShutdownIdAndIsDeletedFalse(pointId, shutdownId)
                .orElseThrow(() -> RestException.notFound("Shutdown isolation point not found: " + pointId));
    }

    private void validateReadinessRequest(UUID shutdownId, UUID itemId,
            PlannedShutdownReadinessItemRequest request) {
        String key = request.readinessKey().trim().toUpperCase(Locale.ROOT);
        boolean duplicate = itemId == null
                ? readinessItemRepository.existsByPlannedShutdownIdAndReadinessKeyAndIsDeletedFalse(shutdownId, key)
                : readinessItemRepository.existsByPlannedShutdownIdAndReadinessKeyAndIdNotAndIsDeletedFalse(
                        shutdownId, key, itemId);
        if (duplicate) throw RestException.conflict("Readiness key is already in use: " + key);
        if (request.responsibleEmployeeId() != null) requireActiveEmployee(request.responsibleEmployeeId());
    }

    private void validateIsolationRequest(UUID shutdownId, UUID pointId,
            PlannedShutdownIsolationPointRequest request) {
        boolean scoped = assetRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(shutdownId)
                .stream().anyMatch(asset -> request.equipmentId().equals(asset.getEquipmentId()));
        if (!scoped) throw RestException.badRequest("Isolation equipment is outside shutdown scope");
        requireActiveEmployee(request.responsibleEmployeeId());
        if (request.permitId() != null && !isCurrentPermitForShutdown(
                request.permitId(), shutdownId, request.equipmentId(), java.time.Instant.now())) {
            throw RestException.badRequest("Safety permit is not current or does not belong to this shutdown");
        }
        String lockTag = request.lockTagIdentifier().trim().toUpperCase(Locale.ROOT);
        boolean duplicate = pointId == null
                ? isolationPointRepository.existsByPlannedShutdownIdAndLockTagIdentifierAndIsDeletedFalse(
                        shutdownId, lockTag)
                : isolationPointRepository.existsByPlannedShutdownIdAndLockTagIdentifierAndIdNotAndIsDeletedFalse(
                        shutdownId, lockTag, pointId);
        if (duplicate) throw RestException.conflict("Isolation lock/tag is already in use: " + lockTag);
        boolean duplicateOrder = pointId == null
                ? isolationPointRepository.existsByPlannedShutdownIdAndOrderNumberAndIsDeletedFalse(
                        shutdownId, request.orderNumber())
                : isolationPointRepository.existsByPlannedShutdownIdAndOrderNumberAndIdNotAndIsDeletedFalse(
                        shutdownId, request.orderNumber(), pointId);
        if (duplicateOrder) throw RestException.conflict("Isolation release order is already in use: "
                + request.orderNumber());
    }

    private void requireActiveEmployee(UUID employeeId) {
        Employee employee = employeeRepository.findByIdAndIsDeletedFalse(employeeId)
                .orElseThrow(() -> RestException.badRequest("Responsible employee not found: " + employeeId));
        if (!employee.isActive()) throw RestException.badRequest("Responsible employee must be active");
    }

    private UUID requireUserActor() {
        UUID actor = scopeAccessService.currentUserIdOrNull();
        if (actor == null) throw RestException.conflict("Authenticated user actor is required");
        return actor;
    }

    private UUID requireEmployeeActor() {
        return scopeAccessService.currentEmployeeId()
                .orElseThrow(() -> RestException.conflict("Authenticated employee actor is required"));
    }

    private PlannedShutdownReadinessPolicy.Facts readinessFacts(
            PlannedShutdown shutdown, java.time.Instant requestedAt) {
        UUID id = shutdown.getId();
        List<PlannedShutdownAsset> assets = assetRepository
                .findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id);
        List<PlannedShutdownWorkItem> work = workItemRepository
                .findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id);
        List<PlannedShutdownReadinessItem> readiness = readinessItemRepository
                .findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id);
        List<PlannedShutdownIsolationPoint> points = isolationPointRepository
                .findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id);
        Set<UUID> isolatedEquipment = points.stream().map(PlannedShutdownIsolationPoint::getEquipmentId)
                .collect(java.util.stream.Collectors.toSet());
        List<PlannedShutdownReadinessPolicy.WorkFact> workFacts = work.stream().map(item -> {
            boolean critical = item.getCriticality() != null && !item.getCriticality().isBlank();
            boolean material = !critical;
            boolean assigned = false;
            var source = workOrderRepository.findByShutdownWorkItemIdAndIsDeletedFalse(item.getId()).orElse(null);
            if (source == null && item.getSourceType() == PlannedShutdownWorkItemSourceType.WORK_ORDER
                    && item.getSourceId() != null) {
                source = workOrderRepository.findByIdAndIsDeletedFalse(item.getSourceId()).orElse(null);
            }
            if (source != null) {
                assigned = workOrderAssignmentEligibilityService.isCurrentlyEligible(source);
                material = !critical || !workOrderMaterialReadinessService.getReadiness(source.getId()).blocking();
            }
            return new PlannedShutdownReadinessPolicy.WorkFact(item.getId(), critical,
                    item.isRequiresIsolation(), material, assigned);
        }).toList();
        List<PlannedShutdownReadinessPolicy.IsolationFact> isolationFacts = new ArrayList<>();
        points.forEach(point -> isolationFacts.add(new PlannedShutdownReadinessPolicy.IsolationFact(point.getId(),
                true, point.getAppliedAt() != null && point.getReleasedAt() == null,
                point.getVerifiedAt() != null && point.getReleasedAt() == null)));
        work.stream().filter(PlannedShutdownWorkItem::isRequiresIsolation)
                .filter(item -> !isolatedEquipment.contains(item.getEquipmentId()))
                .forEach(item -> isolationFacts.add(new PlannedShutdownReadinessPolicy.IsolationFact(
                        item.getId(), false, false, false)));
        java.time.Instant now = requestedAt == null ? java.time.Instant.now() : requestedAt;
        boolean permitsActive = points.stream().filter(point -> point.getReleasedAt() == null)
                .allMatch(point -> point.getPermitId() != null
                        && isCurrentPermitForShutdown(point.getPermitId(), id, point.getEquipmentId(), now));
        List<PlannedShutdownReadinessItem> criticalReadiness = readiness.stream()
                .filter(item -> item.getSeverity() == PlannedShutdownReadinessSeverity.CRITICAL).toList();
        boolean criticalReady = !criticalReadiness.isEmpty() && criticalReadiness.stream()
                .allMatch(item -> item.getStatus() == PlannedShutdownItemStatus.PASSED);
        ApprovalEvidence evidence = currentApprovalEvidence(shutdown);
        boolean approvalCurrent = evidence.currentScope();
        boolean productionApproved = evidence.productionApproved();
        boolean hseApproved = evidence.hseApproved();
        return new PlannedShutdownReadinessPolicy.Facts(id,
                assets.stream().anyMatch(asset -> asset.getDisposition() == PlannedShutdownAssetDisposition.STOPPED
                        || asset.getDisposition() == PlannedShutdownAssetDisposition.RESERVE),
                isCurrentResponsibleEmployee(shutdown), !work.isEmpty(), workFacts,
                productionApproved, hseApproved, approvalCurrent, criticalReady, isolationFacts,
                permitsActive, shutdown.getApprovedStartAt() != null && shutdown.getApprovedEndAt() != null,
                shutdown.getApprovedStartAt(), effectiveEnd(shutdown), now);
    }

    private static java.time.Instant effectiveEnd(PlannedShutdown shutdown) {
        return shutdown.getEffectiveExtensionEndAt() != null
                ? shutdown.getEffectiveExtensionEndAt() : shutdown.getApprovedEndAt();
    }

    private static void bumpWindowVersion(PlannedShutdown shutdown) {
        shutdown.setWindowVersion((shutdown.getWindowVersion() == null ? 1L : shutdown.getWindowVersion()) + 1L);
    }

    private boolean isCurrentResponsibleEmployee(PlannedShutdown shutdown) {
        if (shutdown.getResponsibleEmployeeId() == null) return false;
        return employeeRepository.findByIdAndIsDeletedFalse(shutdown.getResponsibleEmployeeId())
                .filter(Employee::isActive)
                .filter(employee -> Objects.equals(employee.getDepartmentId(), shutdown.getDepartmentId()))
                .isPresent();
    }

    private boolean isCurrentPermitForShutdown(
            UUID permitId, UUID shutdownId, UUID equipmentId, java.time.Instant now) {
        return safetyPermitRepository.findByIdAndIsDeletedFalse(permitId)
                .filter(permit -> permit.getStatus() == SafetyPermitStatus.ISSUED)
                .filter(permit -> permit.getIssuedAt() != null && !permit.getIssuedAt().isAfter(now))
                .filter(permit -> permit.getValidUntil() == null || !permit.getValidUntil().isBefore(now))
                .map(permit -> workOrderRepository.findByIdAndIsDeletedFalse(permit.getWorkOrderId()).orElse(null))
                .filter(workOrder -> Objects.equals(workOrder.getPlannedShutdownId(), shutdownId))
                .filter(workOrder -> Objects.equals(workOrder.getEquipmentId(), equipmentId))
                .filter(workOrder -> workOrder.getShutdownWorkItemId() != null)
                .filter(workOrder -> workItemRepository.findByIdAndPlannedShutdownIdAndIsDeletedFalse(
                        workOrder.getShutdownWorkItemId(), shutdownId)
                        .filter(item -> Objects.equals(item.getEquipmentId(), equipmentId)).isPresent())
                .isPresent();
    }

    private static void requireLifecycle(boolean allowed, String operation, PlannedShutdown shutdown) {
        if (!allowed) throw RestException.conflict(operation + " is not allowed in status "
                + shutdown.getLifecycleStatus());
    }

    private static void apply(PlannedShutdownReadinessItem item, PlannedShutdownReadinessItemRequest request) {
        item.setReadinessKey(request.readinessKey().trim().toUpperCase(Locale.ROOT));
        item.setSourceType(request.sourceType() == null || request.sourceType().isBlank()
                ? null : request.sourceType().trim().toUpperCase(Locale.ROOT));
        item.setSourceId(request.sourceId());
        item.setTitle(request.title().trim());
        item.setSeverity(request.severity());
        item.setResponsibleEmployeeId(request.responsibleEmployeeId());
        item.setDueAt(request.dueAt());
        item.setEvidence(trimToNull(request.evidence()));
        item.setComment(trimToNull(request.comment()));
        item.setOrderNumber(request.orderNumber());
    }

    private static void apply(PlannedShutdownIsolationPoint point, PlannedShutdownIsolationPointRequest request) {
        point.setEquipmentId(request.equipmentId());
        point.setLocationId(request.locationId());
        point.setIsolationMethod(request.isolationMethod().trim());
        point.setLockTagIdentifier(request.lockTagIdentifier().trim().toUpperCase(Locale.ROOT));
        point.setResponsibleEmployeeId(request.responsibleEmployeeId());
        point.setPermitId(request.permitId());
        point.setOrderNumber(request.orderNumber());
    }

    private void audit(String entityType, UUID entityId, AuditAction action, String message, Object before, Object after) {
        auditBuilderService.log(entityType, entityId.toString(), action, AuditModule.PLANNED_SHUTDOWN,
                message, before, after);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private PlannedShutdown findLocked(UUID id) {
        PlannedShutdown shutdown = repository.findByIdAndIsDeletedFalseForUpdate(id)
                .orElseThrow(() -> RestException.notFound("Planned shutdown not found: " + id));
        scopeAccessService.assertCanAccessDepartment(shutdown.getDepartmentId());
        return shutdown;
    }

    private List<PlannedShutdownAssetResponse> assetResponses(UUID id) {
        return assetRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id).stream()
                .map(PlannedShutdownAssetResponse::from).toList();
    }

    private List<PlannedShutdownWorkItemResponse> workItemResponses(UUID id) {
        return workItemRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id).stream()
                .map(PlannedShutdownWorkItemResponse::from).toList();
    }

    private PlannedShutdownWorkItemScopeResponse workItemScope(PlannedShutdown shutdown) {
        return new PlannedShutdownWorkItemScopeResponse(shutdown.getId(), shutdown.getVersion(),
                shutdown.getScopeVersion(), workItemResponses(shutdown.getId()));
    }

    private PlannedShutdownWorkItem findWorkItem(UUID shutdownId, UUID itemId) {
        return workItemRepository.findByIdAndPlannedShutdownIdAndIsDeletedFalse(itemId, shutdownId)
                .orElseThrow(() -> RestException.notFound("Shutdown work item not found: " + itemId));
    }

    private PlannedShutdown incrementScope(PlannedShutdown shutdown) {
        shutdown.setScopeVersion(shutdown.getScopeVersion() + 1);
        shutdown.setApprovalScopeVersion(null);
        shutdown.setApprovalScopeHash(null);
        return repository.saveAndFlush(shutdown);
    }

    private PlannedShutdownDetailResponse transition(UUID id, PlannedShutdownTransitionRequest request,
            PlannedShutdownStatus target, Window oldWindow) {
        PlannedShutdown shutdown = findLocked(id);
        requireVersion(shutdown, request.version());
        return detail(executeTransition(shutdown, target, requireUserActor(), request.reason(),
                request.correlationKey(), oldWindow));
    }

    private PlannedShutdown executeTransition(PlannedShutdown shutdown, PlannedShutdownStatus target, UUID actor,
            String reason, String correlationKey, Window suppliedOldWindow) {
        PlannedShutdownStatus from = shutdown.getLifecycleStatus();
        boolean orthogonalExtension = from == target && suppliedOldWindow != null;
        boolean cancellation = target == PlannedShutdownStatus.CANCELLED && transitionPolicy.canCancel(from);
        boolean rescheduleReset = (target == PlannedShutdownStatus.READINESS_CHECK
                || target == PlannedShutdownStatus.SCOPE_FORMATION)
                && transitionPolicy.canReschedule(from) && suppliedOldWindow != null;
        if (!orthogonalExtension && !cancellation && !rescheduleReset
                && !transitionPolicy.canTransition(from, target)) {
            throw blocker(shutdown, "TRANSITION_NOT_ALLOWED");
        }
        Window oldWindow = suppliedOldWindow == null ? window(shutdown) : suppliedOldWindow;
        Instant now = Instant.now();
        if (target == PlannedShutdownStatus.PENDING_APPROVAL) {
            shutdown.setApprovalScopeVersion(shutdown.getScopeVersion());
            shutdown.setApprovedStartAt(shutdown.getPlannedStartAt());
            shutdown.setApprovedEndAt(shutdown.getPlannedEndAt());
        }
        if (target == PlannedShutdownStatus.SHUTDOWN_STARTED && shutdown.getActualShutdownAt() == null) {
            shutdown.setActualShutdownAt(now);
        } else if (target == PlannedShutdownStatus.SAFE_STATE && shutdown.getActualSafeStateAt() == null) {
            shutdown.setActualSafeStateAt(now);
        } else if (target == PlannedShutdownStatus.REPAIR_IN_PROGRESS && shutdown.getActualRepairStartAt() == null) {
            shutdown.setActualRepairStartAt(now);
        } else if (target == PlannedShutdownStatus.TESTING && shutdown.getActualTestingStartAt() == null) {
            shutdown.setActualTestingStartAt(now);
        } else if (target == PlannedShutdownStatus.STARTUP && shutdown.getActualStartupAt() == null) {
            shutdown.setActualStartupAt(now);
        } else if (target == PlannedShutdownStatus.COMPLETED && shutdown.getActualCompletedAt() == null) {
            shutdown.setActualCompletedAt(now);
        }
        shutdown.setStatus(target);
        PlannedShutdown saved = repository.saveAndFlush(shutdown);
        com.toir.entity.plannedshutdown.PlannedShutdownStatusHistory history =
                new com.toir.entity.plannedshutdown.PlannedShutdownStatusHistory();
        history.setPlannedShutdownId(saved.getId());
        history.setFromStatus(from);
        history.setToStatus(target);
        history.setActorId(actor);
        history.setReason(trimToNull(reason));
        history.setOldEffectiveStartAt(oldWindow.start());
        history.setOldEffectiveEndAt(oldWindow.end());
        Window newWindow = window(saved);
        history.setNewEffectiveStartAt(newWindow.start());
        history.setNewEffectiveEndAt(newWindow.end());
        history.setScopeVersion(saved.getScopeVersion());
        history.setCorrelationKey(trimToNull(correlationKey));
        history.setOccurredAt(now);
        statusHistoryRepository.saveAndFlush(history);
        auditBuilderService.log("planned_shutdown", saved.getId().toString(), AuditAction.UPDATE,
                AuditModule.PLANNED_SHUTDOWN, "Lifecycle transition " + from + " -> " + target,
                from, PlannedShutdownStatusHistoryResponse.from(history));
        return saved;
    }

    private PlannedShutdownDetailResponse detail(PlannedShutdown shutdown) {
        return PlannedShutdownDetailResponse.from(shutdown, assetResponses(shutdown.getId()),
                workItemResponses(shutdown.getId()));
    }

    private PlannedShutdownReadinessAssessment assessReadinessFacts(PlannedShutdown shutdown, Instant at) {
        return readinessPolicy.evaluateReadiness(readinessFacts(shutdown, at));
    }

    private static void requireProceed(PlannedShutdownReadinessAssessment assessment, String prefix, Long version) {
        if (!assessment.canProceed()) {
            String codes = assessment.blockers().stream().map(PlannedShutdownBlocker::code).distinct()
                    .sorted().collect(java.util.stream.Collectors.joining(","));
            throw new PlannedShutdownBlockerException(org.springframework.http.HttpStatus.CONFLICT,
                    prefix + ":" + codes, version, assessment.blockers());
        }
    }

    private void requireAllowedTransition(PlannedShutdown shutdown, PlannedShutdownStatus target) {
        if (!transitionPolicy.canTransition(shutdown.getLifecycleStatus(), target)) {
            throw blocker(shutdown, "TRANSITION_NOT_ALLOWED");
        }
    }

    private void requireCurrentApprovalFacts(PlannedShutdown shutdown, ApprovalRequest approval) {
        if (approval == null || approval.getStatus() != com.toir.enums.ApprovalStatus.APPROVED
                || approval.getActionType() != com.toir.enums.ApprovalActionType.APPROVE
                || approval.getTargetType() != com.toir.enums.ApprovalTargetType.PLANNED_SHUTDOWN
                || !Objects.equals(approval.getTargetId(), shutdown.getId())) {
            throw RestException.conflict("APPROVAL_REQUEST_INVALID");
        }
        if (!Objects.equals(shutdown.getApprovalScopeVersion(), shutdown.getScopeVersion())) {
            throw RestException.conflict("APPROVAL_SCOPE_STALE");
        }
        if (!Objects.equals(shutdown.getApprovalScopeHash(), computeApprovalScopeHash(shutdown))) {
            throw RestException.conflict("APPROVAL_SCOPE_STALE");
        }
        if (!approvalScopeMatches(approval, shutdown.getScopeVersion(), shutdown.getApprovalScopeHash())) {
            throw RestException.conflict("APPROVAL_SCOPE_STALE");
        }
        ApprovalEvidence evidence = approvalEvidence(approval, true);
        if (!evidence.productionApproved()) throw RestException.conflict("APPROVAL_PRODUCTION_MISSING");
        if (!evidence.hseApproved()) throw RestException.conflict("APPROVAL_HSE_MISSING");
        if (Objects.equals(evidence.productionActor(), evidence.hseActor())) {
            throw RestException.conflict("APPROVAL_SEPARATION_OF_DUTY_REQUIRED");
        }
    }

    private ApprovalEvidence currentApprovalEvidence(PlannedShutdown shutdown) {
        boolean current = shutdown.getApprovalScopeVersion() != null
                && Objects.equals(shutdown.getApprovalScopeVersion(), shutdown.getScopeVersion())
                && Objects.equals(shutdown.getApprovalScopeHash(), computeApprovalScopeHash(shutdown));
        if (!current) return new ApprovalEvidence(false, false, false);
        return approvalRequestRepository.findAllByTargetTypeAndTargetIdAndIsDeletedFalse(
                        com.toir.enums.ApprovalTargetType.PLANNED_SHUTDOWN.name(), shutdown.getId()).stream()
                .filter(request -> request.getStatus() == com.toir.enums.ApprovalStatus.APPROVED)
                .filter(request -> request.getActionType() == com.toir.enums.ApprovalActionType.APPROVE)
                .filter(request -> approvalScopeMatches(request, shutdown.getScopeVersion(),
                        shutdown.getApprovalScopeHash()))
                .findFirst().map(request -> approvalEvidence(request, true))
                .orElse(new ApprovalEvidence(false, false, true));
    }

    private static ApprovalEvidence approvalEvidence(ApprovalRequest request, boolean scopeCurrent) {
        UUID production = approvedSeparatedStep(request, PlannedShutdownApprovalScopeHasher.PRODUCTION_APPROVER_ROLE);
        UUID hse = approvedSeparatedStep(request, PlannedShutdownApprovalScopeHasher.HSE_APPROVER_ROLE);
        return new ApprovalEvidence(production != null, hse != null, scopeCurrent, production, hse);
    }

    private static UUID approvedSeparatedStep(ApprovalRequest request, String exactRole) {
        if (request.getSteps() == null) return null;
        return request.getSteps().stream()
                .filter(step -> step.getDecision() == com.toir.enums.ApprovalDecision.APPROVED)
                .filter(step -> exactRole.equals(step.getApproverRole()))
                .filter(step -> step.getDecidedById() != null
                        && !Objects.equals(step.getDecidedById(), request.getRequesterId()))
                .map(ApprovalStep::getDecidedById).findFirst().orElse(null);
    }

    private static boolean approvalScopeMatches(ApprovalRequest request, Long scopeVersion, String scopeHash) {
        if (request.getPayloadJson() == null || scopeVersion == null || scopeHash == null) return false;
        java.util.regex.Matcher matcher = Pattern.compile("\\\"scopeVersion\\\"\\s*:\\s*(\\d+)")
                .matcher(request.getPayloadJson());
        java.util.regex.Matcher hash = Pattern.compile("\\\"scopeHash\\\"\\s*:\\s*\\\"([0-9a-f]{64})\\\"")
                .matcher(request.getPayloadJson());
        return matcher.find() && hash.find() && Objects.equals(Long.valueOf(matcher.group(1)), scopeVersion)
                && Objects.equals(hash.group(1), scopeHash);
    }

    private static String requireReason(String reason, String code, PlannedShutdown shutdown) {
        if (reason == null || reason.isBlank()) {
            throw blocker(org.springframework.http.HttpStatus.BAD_REQUEST, shutdown, code);
        }
        return reason.trim();
    }

    private static Window window(PlannedShutdown shutdown) {
        Instant start = shutdown.getApprovedStartAt() != null ? shutdown.getApprovedStartAt()
                : shutdown.getPlannedStartAt();
        Instant end = effectiveEnd(shutdown);
        if (end == null) end = shutdown.getPlannedEndAt();
        return new Window(start, end);
    }

    private String computeApprovalScopeHash(PlannedShutdown shutdown) {
        UUID id = shutdown.getId();
        return approvalScopeHasher.hash(shutdown,
                assetRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id),
                workItemRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id),
                readinessItemRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id),
                isolationPointRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id));
    }

    private void requireNoActiveWorkOrders(PlannedShutdown shutdown, String code) {
        if (workOrderRepository.existsActiveByPlannedShutdownId(shutdown.getId())) throw blocker(shutdown, code);
    }

    private void requireAllIsolationReleased(PlannedShutdown shutdown, String code) {
        boolean unreleased = isolationPointRepository
                .findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(shutdown.getId()).stream()
                .anyMatch(point -> point.getReleasedAt() == null);
        if (unreleased) throw blocker(shutdown, code);
    }

    private static PlannedShutdownBlockerException blocker(PlannedShutdown shutdown, String code) {
        return blocker(org.springframework.http.HttpStatus.CONFLICT, shutdown, code);
    }

    private static PlannedShutdownBlockerException blocker(org.springframework.http.HttpStatus status,
            PlannedShutdown shutdown, String code) {
        return new PlannedShutdownBlockerException(status, code, shutdown.getVersion(), List.of(
                new PlannedShutdownBlocker(code, code, "PLANNED_SHUTDOWN", shutdown.getId())));
    }

    private static void requireEvidence(PlannedShutdown shutdown, List<PlannedShutdownBlocker> blockers) {
        if (blockers == null || blockers.isEmpty()) return;
        String codes = blockers.stream().map(PlannedShutdownBlocker::code).distinct().sorted()
                .collect(java.util.stream.Collectors.joining(","));
        throw new PlannedShutdownBlockerException(org.springframework.http.HttpStatus.CONFLICT,
                codes, shutdown.getVersion(), blockers);
    }

    private void persistHistory(PlannedShutdown shutdown, PlannedShutdownStatus from, PlannedShutdownStatus to,
            UUID actor, String reason, String correlation, Window oldWindow, Window newWindow, Instant occurredAt) {
        com.toir.entity.plannedshutdown.PlannedShutdownStatusHistory history =
                new com.toir.entity.plannedshutdown.PlannedShutdownStatusHistory();
        history.setPlannedShutdownId(shutdown.getId()); history.setFromStatus(from); history.setToStatus(to);
        history.setActorId(actor); history.setReason(trimToNull(reason));
        history.setOldEffectiveStartAt(oldWindow.start()); history.setOldEffectiveEndAt(oldWindow.end());
        history.setNewEffectiveStartAt(newWindow.start()); history.setNewEffectiveEndAt(newWindow.end());
        history.setScopeVersion(shutdown.getScopeVersion()); history.setCorrelationKey(trimToNull(correlation));
        history.setOccurredAt(occurredAt); statusHistoryRepository.saveAndFlush(history);
    }

    private static String eventCorrelation(String correlation, String suffix) {
        return correlation == null || correlation.isBlank() ? null : correlation.trim() + ":" + suffix;
    }

    private record Window(Instant start, Instant end) {}
    private record ApprovalEvidence(boolean productionApproved, boolean hseApproved, boolean currentScope,
                                    UUID productionActor, UUID hseActor) {
        ApprovalEvidence(boolean productionApproved, boolean hseApproved, boolean currentScope) {
            this(productionApproved, hseApproved, currentScope, null, null);
        }
    }

    private void requireCanonicalAvailable(UUID shutdownId, UUID itemId, PlannedShutdownWorkItemRequest request) {
        boolean duplicateSource = request.sourceId() != null && (itemId == null
                ? workItemRepository.existsByPlannedShutdownIdAndSourceTypeAndSourceIdAndIsDeletedFalse(
                        shutdownId, request.sourceType(), request.sourceId())
                : workItemRepository.existsByPlannedShutdownIdAndSourceTypeAndSourceIdAndIdNotAndIsDeletedFalse(
                        shutdownId, request.sourceType(), request.sourceId(), itemId));
        if (duplicateSource) throw RestException.conflict("Source is already linked to this shutdown");
        boolean duplicateOrder = itemId == null
                ? workItemRepository.existsByPlannedShutdownIdAndOrderNumberAndIsDeletedFalse(
                        shutdownId, request.orderNumber())
                : workItemRepository.existsByPlannedShutdownIdAndOrderNumberAndIdNotAndIsDeletedFalse(
                        shutdownId, request.orderNumber(), itemId);
        if (duplicateOrder) throw RestException.conflict("Work item order number is already in use");
    }

    private void requireScopedSource(UUID shutdownId, PlannedShutdownWorkItemRequest request) {
        boolean scoped = assetRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(shutdownId)
                .stream().anyMatch(asset -> request.equipmentId().equals(asset.getEquipmentId()));
        if (!scoped) throw RestException.badRequest("Work item equipment is outside shutdown scope");
        if (request.sourceType() == PlannedShutdownWorkItemSourceType.MANUAL) return;
        UUID sourceEquipment = switch (request.sourceType()) {
            case DEFECT -> defectRepository.findByIdAndIsDeletedFalse(request.sourceId())
                    .orElseThrow(() -> RestException.notFound("Defect not found: " + request.sourceId()))
                    .getEquipmentId();
            case PPR -> pprTaskRepository.findByIdAndIsDeletedFalse(request.sourceId())
                    .orElseThrow(() -> RestException.notFound("PPR task not found: " + request.sourceId()))
                    .getEquipmentId();
            case REPAIR_REQUEST -> canonicalWorkSourceResolver.resolve(
                    com.toir.enums.RepairCampaignWorkItemSourceType.REPAIR_REQUEST,
                    request.sourceId(), request.equipmentId(), Set.of()).equipmentId();
            case INSPECTION_ROUND -> canonicalWorkSourceResolver.resolve(
                    com.toir.enums.RepairCampaignWorkItemSourceType.INSPECTION_ROUND,
                    request.sourceId(), request.equipmentId(), Set.of()).equipmentId();
            case WORK_ORDER -> workOrderRepository.findByIdAndIsDeletedFalse(request.sourceId())
                    .orElseThrow(() -> RestException.notFound("Work Order not found: " + request.sourceId()))
                    .getEquipmentId();
            case MANUAL, REPAIR_CAMPAIGN -> throw RestException.badRequest("Unsupported work item source type");
        };
        if (!request.equipmentId().equals(sourceEquipment)) {
            throw RestException.badRequest("Work item equipment must match source equipment");
        }
    }

    private static void apply(PlannedShutdownWorkItem item, PlannedShutdownWorkItemRequest request) {
        item.setSourceType(request.sourceType());
        item.setSourceId(request.sourceId());
        item.setEquipmentId(request.equipmentId());
        item.setTitle(request.title().trim());
        item.setPriority(request.priority());
        item.setRequiresShutdown(request.requiresShutdown());
        item.setRequiresIsolation(request.requiresIsolation());
        item.setPlannedDurationMinutes(request.plannedDurationMinutes());
        item.setCriticality(normalizeNullable(request.criticality()));
        item.setOrderNumber(request.orderNumber());
    }

    private PlannedShutdownWorkItem saveWorkItem(PlannedShutdownWorkItem item) {
        try {
            return workItemRepository.saveAndFlush(item);
        } catch (DataIntegrityViolationException ex) {
            String constraint = workItemConstraint(ex);
            if (constraint.contains("uq_planned_shutdown_work_items_active_source")) {
                throw RestException.conflict("Source is already linked to this shutdown");
            }
            if (constraint.contains("uq_planned_shutdown_work_items_active_order")) {
                throw RestException.conflict("Work item order number is already in use");
            }
            throw ex;
        }
    }

    static String workItemConstraint(Throwable error) {
        StringBuilder evidence = new StringBuilder();
        Throwable current = error;
        while (current != null) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException violation
                    && violation.getConstraintName() != null) {
                evidence.append(' ').append(violation.getConstraintName());
            }
            if (current.getMessage() != null) evidence.append(' ').append(current.getMessage());
            current = current.getCause();
        }
        return evidence.toString().toLowerCase(Locale.ROOT);
    }

    private void validateOwnership(UUID departmentId, UUID employeeId) {
        Department ignored = departmentRepository.findByIdAndIsDeletedFalse(departmentId)
                .orElseThrow(() -> RestException.badRequest("Department not found: " + departmentId));
        Employee employee = employeeRepository.findByIdAndIsDeletedFalse(employeeId)
                .orElseThrow(() -> RestException.badRequest("Responsible employee not found: " + employeeId));
        if (!employee.isActive()) {
            throw RestException.badRequest("Responsible employee must be active");
        }
        if (!departmentId.equals(employee.getDepartmentId())) {
            throw RestException.badRequest("Responsible employee must belong to the shutdown department");
        }
    }

    private void validateExistingAssetDepartments(UUID shutdownId, UUID departmentId) {
        List<PlannedShutdownAsset> assets = assetRepository
                .findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(shutdownId);
        if (assets.isEmpty()) return;
        Set<UUID> ids = assets.stream().map(PlannedShutdownAsset::getEquipmentId)
                .collect(java.util.stream.Collectors.toSet());
        List<Equipment> equipment = equipmentRepository.findAllByIdInAndIsDeletedFalse(ids);
        if (equipment.size() != ids.size()) {
            throw RestException.conflict("Existing shutdown scope contains unavailable equipment");
        }
        equipment.forEach(item -> requireEquipmentDepartment(item, departmentId));
    }

    private void validateEquipmentScope(UUID departmentId, List<PlannedShutdownAssetRequest> assets) {
        Set<UUID> equipmentIds = assets.stream().map(PlannedShutdownAssetRequest::equipmentId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, Equipment> equipment = equipmentRepository.findAllByIdInAndIsDeletedFalse(equipmentIds).stream()
                .collect(java.util.stream.Collectors.toMap(Equipment::getId, Function.identity()));
        if (equipment.size() != equipmentIds.size()) {
            throw RestException.badRequest("One or more scope equipment records were not found");
        }
        equipment.values().forEach(item -> requireEquipmentDepartment(item, departmentId));
    }

    private static void requireEquipmentDepartment(Equipment equipment, UUID departmentId) {
        UUID owner = equipment.getResponsibleDepartmentId() != null
                ? equipment.getResponsibleDepartmentId() : equipment.getDepartmentId();
        if (!departmentId.equals(owner)) {
            throw RestException.badRequest("Equipment " + equipment.getId() + " does not belong to shutdown department");
        }
    }

    private static void validateRequestedScope(List<PlannedShutdownAssetRequest> assets) {
        Set<UUID> equipmentIds = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        boolean hasBoundary = false;
        for (PlannedShutdownAssetRequest item : assets) {
            if (item.equipmentId() == null || item.disposition() == null) {
                throw RestException.badRequest("Equipment and disposition are required");
            }
            if (!equipmentIds.add(item.equipmentId())) {
                throw RestException.badRequest("Duplicate equipment in shutdown scope: " + item.equipmentId());
            }
            if (!orders.add(item.orderNumber())) {
                throw RestException.badRequest("Duplicate scope order number: " + item.orderNumber());
            }
            hasBoundary |= item.disposition() == PlannedShutdownAssetDisposition.STOPPED
                    || item.disposition() == PlannedShutdownAssetDisposition.RESERVE;
        }
        if (!hasBoundary) {
            throw RestException.badRequest("Shutdown scope requires at least one STOPPED or RESERVE asset");
        }
    }

    private String normalizeOrGenerateCode(String requested) {
        if (requested != null && !requested.isBlank()) {
            String code = normalizeCode(requested);
            if (repository.existsByCodeAndIsDeletedFalse(code)) {
                throw RestException.conflict("Planned shutdown code already exists: " + code);
            }
            return code;
        }
        for (int i = 0; i < 10; i++) {
            String code = "PS-" + UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 12).toUpperCase(Locale.ROOT);
            if (!repository.existsByCodeAndIsDeletedFalse(code)) return code;
        }
        throw RestException.conflict("Could not allocate a unique planned shutdown code");
    }

    private static String normalizeCode(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw RestException.badRequest("Invalid planned shutdown code");
        }
        return normalized;
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static void validateWindow(java.time.Instant start, java.time.Instant end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw RestException.badRequest("End must be after start");
        }
    }

    private static void requireVersion(PlannedShutdown shutdown, Long expected) {
        if (expected == null || !Objects.equals(shutdown.getVersion(), expected)) {
            throw blocker(shutdown, "VERSION_CONFLICT");
        }
    }

    private PlannedShutdown saveRoot(PlannedShutdown shutdown, String code) {
        try {
            return repository.saveAndFlush(shutdown);
        } catch (DataIntegrityViolationException ex) {
            if (isActiveCodeConstraint(ex)) {
                throw RestException.conflict("Planned shutdown code already exists: " + code);
            }
            throw ex;
        }
    }

    static boolean isActiveCodeConstraint(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException violation
                    && ACTIVE_CODE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(ACTIVE_CODE_CONSTRAINT)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private record PlannedShutdownAuditSnapshot(String code, String name, String type, UUID departmentId,
            UUID responsibleEmployeeId, java.time.Instant startAt, java.time.Instant endAt, String reason,
            String objective, String notes, String riskLevel, java.math.BigDecimal riskScore, Long version) {
        static PlannedShutdownAuditSnapshot from(PlannedShutdown s) {
            return new PlannedShutdownAuditSnapshot(s.getCode(), s.getName(), s.getShutdownType(), s.getDepartmentId(),
                    s.getResponsibleEmployeeId(), s.getPlannedStartAt(), s.getPlannedEndAt(), s.getReason(),
                    s.getObjective(), s.getNotes(), s.getRiskLevel(), s.getRiskScore(), s.getVersion());
        }
    }

    private record ScopeAuditSnapshot(Long scopeVersion, Object assets) {
        static ScopeAuditSnapshot from(Long version, List<PlannedShutdownAsset> assets) {
            return new ScopeAuditSnapshot(version, assets.stream().map(PlannedShutdownAssetResponse::from).toList());
        }
    }

    private record WorkItemAuditSnapshot(Long scopeVersion, PlannedShutdownWorkItemResponse workItem) {
        static WorkItemAuditSnapshot from(Long scopeVersion, PlannedShutdownWorkItem item) {
            return new WorkItemAuditSnapshot(scopeVersion, PlannedShutdownWorkItemResponse.from(item));
        }
    }
}
