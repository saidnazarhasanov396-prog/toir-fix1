package com.toir.service;

import com.toir.dto.plannedshutdown.*;
import com.toir.entity.Department;
import com.toir.entity.PlannedShutdown;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.plannedshutdown.PlannedShutdownAsset;
import com.toir.entity.users.Employee;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.PlanStatus;
import com.toir.enums.PlannedShutdownAssetDisposition;
import com.toir.enums.PlannedShutdownStatus;
import com.toir.exception.RestException;
import com.toir.repository.PlannedShutdownRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownAssetRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PlannedShutdownService {

    private final PlannedShutdownRepository repository;
    private final PlannedShutdownAssetRepository assetRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final EquipmentRepository equipmentRepository;
    private final AuditBuilderService auditBuilderService;

    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z0-9-]{3,64}");
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
        PlannedShutdown saved = repository.saveAndFlush(s);

        auditBuilderService.log(
                "planned_shutdown",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.PLANNED_SHUTDOWN,
                "Плановая остановка создана",
                null,
                saved
        );

        return PlannedShutdownDetailResponse.from(saved, List.of());
    }

    @Transactional(readOnly = true)
    public PlannedShutdownDetailResponse get(UUID id) {
        PlannedShutdown shutdown = find(id);
        return PlannedShutdownDetailResponse.from(shutdown, assetResponses(id));
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
        PlannedShutdown saved = repository.saveAndFlush(shutdown);
        auditBuilderService.log("planned_shutdown", id.toString(), AuditAction.UPDATE,
                AuditModule.PLANNED_SHUTDOWN, "Плановая остановка обновлена", before,
                PlannedShutdownAuditSnapshot.from(saved));
        return PlannedShutdownDetailResponse.from(saved, assetResponses(id));
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

        Set<UUID> equipmentIds = new LinkedHashSet<>();
        request.assets().forEach(item -> equipmentIds.add(item.equipmentId()));
        Map<UUID, Equipment> equipment = equipmentRepository.findAllByIdInAndIsDeletedFalse(equipmentIds).stream()
                .collect(java.util.stream.Collectors.toMap(Equipment::getId, Function.identity()));
        if (equipment.size() != equipmentIds.size()) {
            throw RestException.badRequest("One or more scope equipment records were not found");
        }
        for (Equipment item : equipment.values()) {
            requireEquipmentDepartment(item, shutdown.getDepartmentId());
        }

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
}
