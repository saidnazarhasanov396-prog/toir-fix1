package com.toir.service;

import com.toir.dto.plannedshutdown.*;
import com.toir.entity.Department;
import com.toir.entity.PlannedShutdown;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.plannedshutdown.PlannedShutdownAsset;
import com.toir.entity.plannedshutdown.PlannedShutdownWorkItem;
import com.toir.entity.users.Employee;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.PlanStatus;
import com.toir.enums.PlannedShutdownAssetDisposition;
import com.toir.enums.PlannedShutdownStatus;
import com.toir.enums.PlannedShutdownWorkItemSourceType;
import com.toir.exception.RestException;
import com.toir.repository.PlannedShutdownRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownAssetRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownWorkItemRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.util.AuditBuilderService;
import com.toir.service.plannedshutdown.PlannedShutdownWorkItemPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PlannedShutdownService {

    private final PlannedShutdownRepository repository;
    private final PlannedShutdownAssetRepository assetRepository;
    private final PlannedShutdownWorkItemRepository workItemRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final EquipmentRepository equipmentRepository;
    private final DefectRepository defectRepository;
    private final PprTaskRepository pprTaskRepository;
    private final WorkOrderRepository workOrderRepository;
    private final PlannedShutdownWorkItemPolicy workItemPolicy;
    private final AuditBuilderService auditBuilderService;

    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z0-9-]{3,64}");
    private static final String ACTIVE_CODE_CONSTRAINT = "uq_planned_shutdowns_active_code";
    private static final Set<PlannedShutdownStatus> SCOPE_MUTABLE_STATUSES = EnumSet.of(
            PlannedShutdownStatus.DRAFT, PlannedShutdownStatus.SCOPE_FORMATION);


    @Transactional(readOnly = true)
    public List<PlannedShutdownDto> findAllFiltered(UUID departmentId, PlanStatus status, String search) {
        String statusStr = status != null ? status.name() : null;
        String searchPattern = (search != null && !search.isBlank()) ? "%" + search.trim().toLowerCase() + "%" : null;
        return repository.findAllFiltered(departmentId, statusStr, searchPattern).stream()
                .map(PlannedShutdownDto::from).toList();
    }

    @Transactional
    public PlannedShutdownDetailResponse create(PlannedShutdownCreateRequest r) {
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
        validateWindow(r.startAt(), r.endAt());
        validateOwnership(r.departmentId(), r.responsibleEmployeeId());
        String code = normalizeCode(r.code());
        if (repository.existsByCodeAndIdNotAndIsDeletedFalse(code, id)) {
            throw RestException.conflict("Planned shutdown code already exists: " + code);
        }
        validateExistingAssetDepartments(id, r.departmentId());
        PlannedShutdownAuditSnapshot before = PlannedShutdownAuditSnapshot.from(shutdown);
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
        shutdown.setScopeVersion(shutdown.getScopeVersion() + 1);
        PlannedShutdown saved = repository.saveAndFlush(shutdown);
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

    @Transactional
    public PlannedShutdownDto finalizeApprovalFromApprovalRequest(UUID id) {
        PlannedShutdown s = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Planned shutdown not found: " + id));
        s.setStatus(PlanStatus.APPROVED);

        PlannedShutdown saved = repository.save(s);

        auditBuilderService.log(
                "planned_shutdown",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.PLANNED_SHUTDOWN,
                "Плановая остановка обновлена",
                s,
                saved
        );

        return PlannedShutdownDto.from(s);
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

    private PlannedShutdown find(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Planned shutdown not found: " + id));
    }

    private PlannedShutdown findLocked(UUID id) {
        return repository.findByIdAndIsDeletedFalseForUpdate(id)
                .orElseThrow(() -> RestException.notFound("Planned shutdown not found: " + id));
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
        return repository.saveAndFlush(shutdown);
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
            throw RestException.conflict("Planned shutdown was changed; expected version " + expected
                    + " but found " + shutdown.getVersion());
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
