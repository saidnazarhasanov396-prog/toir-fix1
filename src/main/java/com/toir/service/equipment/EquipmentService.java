package com.toir.service.equipment;

import com.toir.dto.equipment.*;
import com.toir.repository.equipment.EquipmentStatsProjection;
import com.toir.dto.warehouse.WarehouseEquipmentAssignRequest;
import com.toir.entity.Department;
import com.toir.entity.DowntimeEvent;
import com.toir.entity.Location;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentPassport;
import com.toir.entity.equipment.EquipmentType;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairRequest;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseEquipmentItem;
import com.toir.enums.AuditAction;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.PlacementType;
import com.toir.enums.PlacementTargetType;
import com.toir.enums.WarehouseEquipmentStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkType;
import com.toir.exception.RestException;
import com.toir.repository.WarehouseEquipmentItemRepository;
import com.toir.repository.DowntimeEventRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.LocationRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentPassportRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentTypeRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.service.WarehouseEquipmentItemService;
import com.toir.util.AuditBuilderService;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EquipmentService {

    private final EquipmentRepository repository;
    private final DepartmentRepository departmentRepository;
    private final LocationRepository locationRepository;
    private final EquipmentTypeRepository equipmentTypeRepository;
    private final EquipmentPassportRepository passportRepository;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseEquipmentItemRepository warehouseEquipmentItemRepository;
    private final RepairRequestRepository repairRequestRepository;
    private final DefectRepository defectRepository;
    private final WorkOrderRepository workOrderRepository;
    private final DowntimeEventRepository downtimeEventRepository;
    private final EquipmentAttributeService equipmentAttributeService;
    private final EquipmentManualAttributeService equipmentManualAttributeService;
    private final WarehouseEquipmentItemService warehouseEquipmentItemService;
    private final EquipmentStatusLifecycleService equipmentStatusLifecycleService;
    private final AuditBuilderService auditBuilderService;
    @Value("${app.features.manual-attributes.write-enabled:false}")
    private boolean manualAttributeWritesEnabled;
    private static final Set<WorkOrderStatus> FINAL_WORK_ORDER_STATUSES =
            EnumSet.of(WorkOrderStatus.COMPLETED, WorkOrderStatus.CLOSED, WorkOrderStatus.CANCELLED);


    @Transactional(readOnly = true)
    public Page<EquipmentDto> search(UUID departmentId,
                                     UUID equipmentTypeId,
                                     EquipmentStatus status,
                                     EquipmentCategory category,
                                     UUID warehouseId,
                                     boolean availableForReplacement,
                                     String search,
                                     int page,
                                     int pageSize) {
        String searchPattern = null;
        if (search != null && !search.isBlank()) {
            searchPattern = "%" + search.trim().toLowerCase() + "%";
        }
        Page<Equipment> items;
        if (availableForReplacement) {
            if (warehouseId == null) {
                throw RestException.badRequest("warehouseId is required when availableForReplacement is true");
            }
            warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)
                    .orElseThrow(() -> RestException.notFound("Warehouse not found: " + warehouseId));
            items = repository.searchAvailableForReplacement(
                    warehouseId,
                    WarehouseEquipmentStatus.AVAILABLE,
                    WorkType.REPLACEMENT,
                    FINAL_WORK_ORDER_STATUSES,
                    departmentId,
                    equipmentTypeId,
                    status,
                    category,
                    searchPattern,
                    PaginationUtils.pageRequest(page, pageSize)
            );
        } else {
            items = repository.search(
                    departmentId,
                    equipmentTypeId,
                    status,
                    category,
                    searchPattern,
                    PaginationUtils.pageRequest(page, pageSize)
            );
        }
        return enrich(items);
    }

    @Transactional(readOnly = true)
    public EquipmentDto findById(UUID id) {
        return enrich(List.of(getOrThrow(id))).getFirst();
    }

    @Transactional(readOnly = true)
    public EquipmentDetailDto findDetailById(UUID id) {
        EquipmentDto equipment = findById(id);

        List<RepairRequest> repairRequestEntities = repairRequestRepository.search(null, null, id);
        if (repairRequestEntities == null) {
            repairRequestEntities = List.of();
        }
        List<EquipmentDetailDto.RepairRequestShortDto> repairRequests = repairRequestEntities.stream()
                .map(r -> new EquipmentDetailDto.RepairRequestShortDto(
                        r.getId(),
                        r.getNumber(),
                        r.getTitle(),
                        r.getStatus(),
                        r.getDetectedAt(),
                        r.getDescription()
                ))
                .toList();

        List<Defect> defectEntities = defectRepository.findAllByEquipmentIdAndIsDeletedFalse(id);
        if (defectEntities == null) {
            defectEntities = List.of();
        }
        List<EquipmentDetailDto.DefectShortDto> defects = defectEntities.stream()
                .map(d -> new EquipmentDetailDto.DefectShortDto(
                        d.getId(),
                        d.getCode(),
                        d.getTitle(),
                        d.getStatus(),
                        d.getDetectedAt(),
                        d.getDescription()
                ))
                .toList();

        List<WorkOrder> workOrderEntities = workOrderRepository.search(null, null, id);
        if (workOrderEntities == null) {
            workOrderEntities = List.of();
        }
        List<EquipmentDetailDto.WorkOrderShortDto> workOrders = workOrderEntities.stream()
                .map(w -> new EquipmentDetailDto.WorkOrderShortDto(
                        w.getId(),
                        w.getNumber(),
                        w.getTitle(),
                        w.getStatus(),
                        w.getStartedAt(),
                        w.getCompletedAt(),
                        w.getSummary()
                ))
                .toList();

        List<DowntimeEvent> downtimeEventEntities = downtimeEventRepository
                .findAllByEquipmentIdAndIsDeletedFalseOrderByStartAtDesc(id);
        if (downtimeEventEntities == null) {
            downtimeEventEntities = List.of();
        }
        List<EquipmentDetailDto.DowntimeEventShortDto> downtimeEvents = downtimeEventEntities.stream()
                .map(d -> new EquipmentDetailDto.DowntimeEventShortDto(
                        d.getId(),
                        d.getStartAt(),
                        d.getEndAt(),
                        d.getDurationMinutes(),
                        d.getType(),
                        d.getDescription()
                ))
                .toList();

        return new EquipmentDetailDto(
                equipment,
                repairRequests,
                defects,
                workOrders,
                downtimeEvents,
                equipmentAttributeService == null ? List.of() : equipmentAttributeService.findValues(id),
                equipmentManualAttributeService == null ? List.of() : equipmentManualAttributeService.list(id)
        );
    }

    @Transactional(readOnly = true)
    public Page<EquipmentDto> findChildren(UUID parentId, int page, int pageSize) {
        getOrThrow(parentId);
        return enrich(repository.findAllByParentIdAndIsDeletedFalse(parentId, PaginationUtils.pageRequest(page, pageSize)));
    }

    @Transactional
    public EquipmentDto create(EquipmentCreateRequest request) {
        assertManualAttributesAllowed(request.manualAttributes());
        validateClientProvidedCode(request.code());
        validateAverageOperatingLifeForCreate(request.averageOperatingLifeHours());
        validateCreatePlacement(request.departmentId(), request.warehouseId());
        validateDepartmentExists(request.departmentId());
        validateWarehouseExists(request.warehouseId());
        if (repository.existsByInventoryNumberAndIsDeletedFalse(request.inventoryNumber())) {
            throw RestException.conflict("Inventory number already exists: " + request.inventoryNumber());
        }
        validateParent(null, request.parentId());
        Equipment entity = new Equipment();
        entity.setCode(nextCode());
        apply(entity, request);
        Equipment saved = repository.save(entity);
        if (equipmentAttributeService != null) {
            equipmentAttributeService.upsertValues(
                    saved,
                    request.attributes() == null ? List.of() : request.attributes()
            );
        }
        if (equipmentManualAttributeService != null && request.manualAttributes() != null && !request.manualAttributes().isEmpty()) {
            equipmentManualAttributeService.replaceAll(
                    saved.getId(),
                    new com.toir.dto.equipmentmanualattribute.BulkEquipmentManualAttributeRequest(request.manualAttributes())
            );
        }
        if (request.warehouseId() != null) {
            warehouseEquipmentItemService.assign(
                    request.warehouseId(),
                    new WarehouseEquipmentAssignRequest(saved.getId(), null)
            );
        }

        auditBuilderService.log(
                "equipment",
                saved.getId().toString(),
                com.toir.enums.AuditAction.CREATE,
                com.toir.enums.AuditModule.EQUIPMENT,
                "Оборудование создано: код=%s, наименование=%s".formatted(saved.getCode(), saved.getName()),
                null,
                saved
        );
        return enrich(List.of(saved)).getFirst();
    }

    @Transactional
    public EquipmentDto update(UUID id, EquipmentUpdateRequest request) {
        assertManualAttributesAllowed(request.manualAttributes());
        Equipment entity = getOrThrow(id);
        validateClientProvidedCode(request.code());
        validateNoDirectStatusChange(entity, request.status());
        validateAverageOperatingLifeForUpdate(request.averageOperatingLifeHours());
        validateDepartmentExists(request.departmentId());

        boolean equipmentTypeChanged = isEquipmentTypeChanged(entity.getEquipmentTypeId(), request.equipmentTypeId());
        validateAttributesForTypeChange(equipmentTypeChanged, request.attributes());

        applyForUpdate(entity, request);
        validateParent(entity.getId(), entity.getParentId());

        Equipment saved = repository.save(entity);
        if (equipmentAttributeService != null && request.attributes() != null) {
            equipmentAttributeService.upsertValues(saved, request.attributes());
        }
        if (equipmentManualAttributeService != null && request.manualAttributes() != null && !request.manualAttributes().isEmpty()) {
            equipmentManualAttributeService.replaceAll(
                    saved.getId(),
                    new com.toir.dto.equipmentmanualattribute.BulkEquipmentManualAttributeRequest(request.manualAttributes())
            );
        }

        auditBuilderService.log(
                "equipment",
                saved.getId().toString(),
                AuditAction.UPDATE,
                com.toir.enums.AuditModule.EQUIPMENT,
                "Оборудование обновлено: код=%s, наименование=%s".formatted(saved.getCode(), saved.getName()),
                entity,
                saved);
        return enrich(List.of(saved)).getFirst();
    }

    @Transactional
    public EquipmentDto updatePlacement(UUID id, EquipmentPlacementRequest request) {
        Equipment equipment = getOrThrow(id);
        validatePlacementRequest(request);
        if (request.targetType() == PlacementTargetType.WAREHOUSE) {
            return moveToWarehouse(equipment, request);
        }
        return moveToDepartment(equipment, request);
    }

    @Transactional
    public void delete(UUID id) {
        Equipment entity = getOrThrow(id);
        if (!repository.findAllByParentIdAndIsDeletedFalse(id).isEmpty()) {
            throw RestException.conflict("Equipment has child equipment");
        }

        entity.setDeleted(true);
        Equipment saved = repository.save(entity);

        auditBuilderService.log(
                "equipment",
                saved.getId().toString(),
                AuditAction.DELETE,
                com.toir.enums.AuditModule.EQUIPMENT,
                "Оборудование удалено: код=%s, наименование=%s".formatted(saved.getCode(), saved.getName()),
                saved,
                null);
    }

    Equipment getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + id));
    }

    public Page<EquipmentDto> enrich(Page<Equipment> items) {
        if (items.isEmpty()) return items.map(EquipmentDto::from);

        Map<UUID, EquipmentDto> enrichedById = enrich(items.getContent()).stream()
                .collect(Collectors.toMap(EquipmentDto::id, Function.identity(), (a, b) -> a));
        return items.map(e -> enrichedById.get(e.getId()));
    }

    private List<EquipmentDto> enrich(List<Equipment> items) {
        if (items.isEmpty()) return Collections.emptyList();

        Set<UUID> deptIds = collectIds(items, Equipment::getDepartmentId);
        Set<UUID> locIds = collectIds(items, Equipment::getLocationId);
        Set<UUID> typeIds = collectIds(items, Equipment::getEquipmentTypeId);
        Set<UUID> parentIds = collectIds(items, Equipment::getParentId);
        Set<UUID> equipmentIds = items.stream().map(Equipment::getId).collect(Collectors.toSet());

        Map<UUID, Department> deptMap = byId(departmentRepository.findAllByIdInAndIsDeletedFalse(deptIds), Department::getId);
        Map<UUID, Location> locMap = byId(locationRepository.findAllByIdInAndIsDeletedFalse(locIds), Location::getId);
        Set<UUID> unresolvedLocIds = locIds.stream()
                .filter(id -> !locMap.containsKey(id))
                .collect(Collectors.toSet());
        Map<UUID, WarehouseEquipmentItem> activeWarehouseItemMap = byId(
                warehouseEquipmentItemRepository.findActiveByEquipmentIds(equipmentIds),
                WarehouseEquipmentItem::getEquipmentId
        );
        Set<UUID> warehouseIdsToLoad = new HashSet<>(unresolvedLocIds);
        warehouseIdsToLoad.addAll(
                activeWarehouseItemMap.values().stream()
                        .map(WarehouseEquipmentItem::getWarehouseId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet())
        );
        Map<UUID, Warehouse> warehouseMap = warehouseIdsToLoad.isEmpty()
                ? Collections.emptyMap()
                : byId(warehouseRepository.findAllByIdInAndIsDeletedFalse(warehouseIdsToLoad), Warehouse::getId);
        Map<UUID, EquipmentType> typeMap = byId(equipmentTypeRepository.findAllByIdInAndIsDeletedFalse(typeIds), EquipmentType::getId);
        Map<UUID, Equipment> parentMap = byId(repository.findAllByIdInAndIsDeletedFalse(parentIds), Equipment::getId);
        Map<UUID, EquipmentPassport> passportMap = passportRepository
                .findAllByEquipmentIdInAndIsDeletedFalse(equipmentIds).stream()
                .collect(Collectors.toMap(EquipmentPassport::getEquipmentId, Function.identity(), (a, b) -> a));

        return items.stream()
                .map(e -> {
                    EquipmentDto.Ref departmentRef = deptRef(deptMap.get(e.getDepartmentId()));
                    EquipmentDto.Ref locationRef = locRef(e.getLocationId(), locMap, warehouseMap);
                    WarehouseEquipmentItem activeWarehouseItem = activeWarehouseItemMap.get(e.getId());
                    EquipmentDto.Ref warehouseRef = warehouseRef(
                            activeWarehouseItem == null ? null : warehouseMap.get(activeWarehouseItem.getWarehouseId())
                    );
                    EquipmentDto.PlacementRef placement = placementRef(
                            e,
                            departmentRef,
                            warehouseRef,
                            activeWarehouseItem,
                            locationRef
                    );
                    return EquipmentDto.from(
                            e,
                            departmentRef,
                            locationRef,
                            typeRef(typeMap.get(e.getEquipmentTypeId())),
                            parentRef(parentMap.get(e.getParentId())),
                            passportRef(passportMap.get(e.getId())),
                            placement
                    );
                })
                .toList();
    }

    private static Set<UUID> collectIds(List<Equipment> items, Function<Equipment, UUID> getter) {
        Set<UUID> ids = new HashSet<>();
        for (Equipment e : items) {
            UUID id = getter.apply(e);
            if (id != null) ids.add(id);
        }
        return ids;
    }

    private static <T> Map<UUID, T> byId(Collection<T> list, Function<T, UUID> getter) {
        return list.stream().collect(Collectors.toMap(getter, Function.identity(), (a, b) -> a));
    }

    private static EquipmentDto.Ref deptRef(Department d) {
        return d == null ? null : new EquipmentDto.Ref(d.getId(), d.getCode(), d.getName());
    }

    private static EquipmentDto.Ref locRef(Location l) {
        return l == null ? null : new EquipmentDto.Ref(l.getId(), l.getCode(), l.getName());
    }

    private static EquipmentDto.Ref warehouseRef(Warehouse warehouse) {
        return warehouse == null ? null : new EquipmentDto.Ref(warehouse.getId(), warehouse.getCode(), warehouse.getName());
    }

    private static EquipmentDto.Ref locRef(UUID locationId,
                                           Map<UUID, Location> locationMap,
                                           Map<UUID, Warehouse> warehouseMap) {
        if (locationId == null) {
            return null;
        }
        Location location = locationMap.get(locationId);
        if (location != null) {
            return locRef(location);
        }
        Warehouse warehouse = warehouseMap.get(locationId);
        return warehouse == null ? null : new EquipmentDto.Ref(warehouse.getId(), warehouse.getCode(), warehouse.getName());
    }

    private static EquipmentDto.Ref typeRef(EquipmentType t) {
        return t == null ? null : new EquipmentDto.Ref(t.getId(), t.getCode(), t.getName());
    }

    private static EquipmentDto.Ref parentRef(Equipment p) {
        return p == null ? null : new EquipmentDto.Ref(p.getId(), p.getCode(), p.getName());
    }

    private static EquipmentDto.PassportRef passportRef(EquipmentPassport p) {
        return p == null ? null : new EquipmentDto.PassportRef(
                p.getPassportNumber(), p.getPowerKw(), p.getVoltageV(), p.getPressureBar());
    }

    private static EquipmentDto.PlacementRef placementRef(Equipment equipment,
                                                          EquipmentDto.Ref departmentRef,
                                                          EquipmentDto.Ref warehouseRef,
                                                          WarehouseEquipmentItem activeWarehouseItem,
                                                          EquipmentDto.Ref locationRef) {
        if (equipment.getDepartmentId() != null) {
            if (activeWarehouseItem == null) {
                return new EquipmentDto.PlacementRef(
                        PlacementType.DEPARTMENT,
                        departmentRef,
                        null,
                        null,
                        locationRef
                );
            }
            return new EquipmentDto.PlacementRef(
                    PlacementType.DEPARTMENT,
                    departmentRef,
                    warehouseRef,
                    activeWarehouseItem.getStatus(),
                    locationRef
            );
        }

        if (activeWarehouseItem != null) {
            EquipmentDto.Ref placementLocation = locationRef;
            if (warehouseRef != null && (
                    Objects.equals(equipment.getLocationId(), activeWarehouseItem.getWarehouseId())
                            || (locationRef != null && Objects.equals(locationRef.id(), activeWarehouseItem.getWarehouseId()))
            )) {
                placementLocation = warehouseRef;
            }
            return new EquipmentDto.PlacementRef(
                    PlacementType.WAREHOUSE,
                    null,
                    warehouseRef,
                    activeWarehouseItem.getStatus(),
                    placementLocation
            );
        }

        return new EquipmentDto.PlacementRef(
                PlacementType.UNKNOWN,
                null,
                null,
                null,
                locationRef
        );
    }

    private void apply(Equipment entity, EquipmentCreateRequest request) {
        entity.setName(request.name());
        entity.setInventoryNumber(request.inventoryNumber());
        entity.setTechnicalNumber(request.technicalNumber());
        entity.setSerialNumber(request.serialNumber());
        entity.setModel(request.model());
        entity.setEquipmentTypeId(request.equipmentTypeId());
        entity.setDepartmentId(request.departmentId());
        entity.setLocationId(request.locationId());
        entity.setParentId(request.parentId());
        entity.setCriticalityClassId(request.criticalityClassId());
        entity.setResponsibleId(request.responsibleId());
        entity.setManufacturer(request.manufacturer());
        if (request.status() != null) entity.setStatus(request.status());
        entity.setCategory(request.category() != null ? request.category() : EquipmentCategory.PRODUCTION_EQUIPMENT);
        entity.setCommissionedAt(request.commissionedAt());
        entity.setWarrantyUntil(request.warrantyUntil());
        entity.setAverageOperatingLifeHours(request.averageOperatingLifeHours());
        entity.setDescription(request.description());
    }

    private EquipmentDto moveToWarehouse(Equipment equipment, EquipmentPlacementRequest request) {
        WarehouseEquipmentStatus targetStatus = resolveWarehousePlacementStatus(request.warehouseStatus());
        warehouseEquipmentItemService.transferEquipmentToWarehouse(
                equipment.getId(),
                request.warehouseId(),
                targetStatus
        );
        equipment.setDepartmentId(null);
        equipment.setLocationId(request.warehouseId());
        Equipment saved = repository.save(equipment);
        return enrich(List.of(saved)).getFirst();
    }

    private EquipmentDto moveToDepartment(Equipment equipment, EquipmentPlacementRequest request) {
        UUID departmentId = request.departmentId();
        WarehouseEquipmentItem activeWarehouseItem = warehouseEquipmentItemRepository.findActiveByEquipmentId(equipment.getId())
                .orElseThrow(() -> RestException.badRequest("Active warehouse assignment is required for DEPARTMENT target"));
        if (activeWarehouseItem.getStatus() == WarehouseEquipmentStatus.OUT_OF_SERVICE) {
            throw RestException.badRequest("OUT_OF_SERVICE equipment cannot be installed directly");
        }
        warehouseEquipmentItemService.updateStatus(
                activeWarehouseItem.getWarehouseId(),
                equipment.getId(),
                WarehouseEquipmentStatus.INSTALLED,
                departmentId
        );
        equipment.setDepartmentId(departmentId);
        if (Objects.equals(equipment.getLocationId(), activeWarehouseItem.getWarehouseId())) {
            equipment.setLocationId(null);
        }
        Equipment saved = repository.save(equipment);
        return enrich(List.of(saved)).getFirst();
    }

    private void validatePlacementRequest(EquipmentPlacementRequest request) {
        if (request == null) {
            throw RestException.badRequest("Placement request is required");
        }
        if (request.targetType() == null) {
            throw RestException.badRequest("targetType is required");
        }
        if (request.warehouseId() != null && request.departmentId() != null) {
            throw RestException.badRequest("warehouseId and departmentId cannot both be provided");
        }

        if (request.targetType() == PlacementTargetType.WAREHOUSE) {
            if (request.warehouseId() == null) {
                throw RestException.badRequest("warehouseId is required when targetType is WAREHOUSE");
            }
            if (request.departmentId() != null) {
                throw RestException.badRequest("departmentId must be null when targetType is WAREHOUSE");
            }
            resolveWarehousePlacementStatus(request.warehouseStatus());
            validateWarehouseExists(request.warehouseId());
            return;
        }

        if (request.departmentId() == null) {
            throw RestException.badRequest("departmentId is required when targetType is DEPARTMENT");
        }
        if (request.warehouseId() != null) {
            throw RestException.badRequest("warehouseId must be null when targetType is DEPARTMENT");
        }
        if (request.warehouseStatus() != null) {
            throw RestException.badRequest("warehouseStatus must be null when targetType is DEPARTMENT");
        }
        validateDepartmentExists(request.departmentId());
    }

    private WarehouseEquipmentStatus resolveWarehousePlacementStatus(WarehouseEquipmentStatus warehouseStatus) {
        if (warehouseStatus == null) {
            return WarehouseEquipmentStatus.AVAILABLE;
        }
        if (warehouseStatus != WarehouseEquipmentStatus.AVAILABLE
                && warehouseStatus != WarehouseEquipmentStatus.OUT_OF_SERVICE) {
            throw RestException.badRequest("warehouseStatus for WAREHOUSE target must be AVAILABLE or OUT_OF_SERVICE");
        }
        return warehouseStatus;
    }

    private void validateClientProvidedCode(String code) {
        if (code != null && !code.isBlank()) {
            throw RestException.badRequest("Equipment code is generated by system and must not be provided");
        }
    }

    private void assertManualAttributesAllowed(List<?> manualAttributes) {
        if (manualAttributes == null || manualAttributes.isEmpty()) {
            return;
        }
        if (!manualAttributeWritesEnabled) {
            throw RestException.badRequest(EquipmentManualAttributeService.WRITE_DISABLED_MESSAGE);
        }
    }

    private boolean isEquipmentTypeChanged(UUID currentEquipmentTypeId, UUID requestedEquipmentTypeId) {
        return requestedEquipmentTypeId != null && !Objects.equals(currentEquipmentTypeId, requestedEquipmentTypeId);
    }

    private void validateAttributesForTypeChange(boolean equipmentTypeChanged, List<?> attributes) {
        if (equipmentTypeChanged && attributes == null) {
            throw RestException.badRequest("Attributes are required when equipment type changes.");
        }
    }

    private void validateCreatePlacement(UUID departmentId, UUID warehouseId) {
        if (departmentId == null && warehouseId == null) {
            throw RestException.badRequest("departmentId or warehouseId is required");
        }
    }

    private void validateAverageOperatingLifeForCreate(Long averageOperatingLifeHours) {
        if (averageOperatingLifeHours == null) {
            throw RestException.badRequest("averageOperatingLifeHours is required");
        }
        validateAverageOperatingLifeForUpdate(averageOperatingLifeHours);
    }

    private void validateAverageOperatingLifeForUpdate(Long averageOperatingLifeHours) {
        if (averageOperatingLifeHours != null && averageOperatingLifeHours <= 0) {
            throw RestException.badRequest("averageOperatingLifeHours must be positive");
        }
    }

    private void validateDepartmentExists(UUID departmentId) {
        if (departmentId == null) {
            return;
        }
        departmentRepository.findByIdAndIsDeletedFalse(departmentId)
                .orElseThrow(() -> RestException.notFound("Department not found: " + departmentId));
    }

    private void validateWarehouseExists(UUID warehouseId) {
        if (warehouseId == null) {
            return;
        }
        warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)
                .orElseThrow(() -> RestException.notFound("Warehouse not found: " + warehouseId));
    }

    private String nextCode() {
        int year = Year.now().getValue();
        String codePrefix = "EQ-" + year + "-";
        long sequence = repository.maxSequenceByCodePrefix(codePrefix) + 1;
        String code = formatCode("EQ", year, sequence);
        while (repository.existsByCodeAndIsDeletedFalse(code)) {
            sequence++;
            code = formatCode("EQ", year, sequence);
        }
        return code;
    }

    private String formatCode(String prefix, int year, long sequence) {
        return "%s-%d-%04d".formatted(prefix, year, sequence);
    }

    private void applyForUpdate(Equipment entity, EquipmentUpdateRequest request) {
        entity.setName(request.name() != null ? request.name() : entity.getName());
        entity.setInventoryNumber(request.inventoryNumber()  != null ? request.inventoryNumber() : entity.getInventoryNumber());
        entity.setTechnicalNumber(request.technicalNumber() != null ? request.technicalNumber() : entity.getTechnicalNumber());
        entity.setSerialNumber(request.serialNumber()  != null ? request.serialNumber() : entity.getSerialNumber());
        entity.setModel(request.model() != null ? request.model() : entity.getModel());
        entity.setEquipmentTypeId(request.equipmentTypeId() != null ? request.equipmentTypeId() : entity.getEquipmentTypeId());
        entity.setDepartmentId(request.departmentId() != null ? request.departmentId() : entity.getDepartmentId());
        entity.setLocationId(request.locationId() != null ? request.locationId() : entity.getLocationId());
        entity.setParentId(request.parentId());
        entity.setCriticalityClassId(request.criticalityClassId()  != null ? request.criticalityClassId() : entity.getCriticalityClassId());
        entity.setResponsibleId(request.responsibleId() != null ? request.responsibleId() : entity.getResponsibleId());
        entity.setManufacturer(request.manufacturer() != null ? request.manufacturer() : entity.getManufacturer());
        entity.setCategory(request.category() != null ? request.category() : entity.getCategory());
        entity.setCommissionedAt(request.commissionedAt() != null ? request.commissionedAt() : entity.getCommissionedAt());
        entity.setWarrantyUntil(request.warrantyUntil() != null ? request.warrantyUntil() : entity.getWarrantyUntil());
        entity.setAverageOperatingLifeHours(request.averageOperatingLifeHours() != null
                ? request.averageOperatingLifeHours()
                : entity.getAverageOperatingLifeHours());
        entity.setDescription(request.description() != null ? request.description() : entity.getDescription());
    }

    private void validateNoDirectStatusChange(Equipment entity, EquipmentStatus requestedStatus) {
        if (requestedStatus == null || requestedStatus == entity.getStatus()) {
            return;
        }
        throw RestException.badRequest("Equipment status changes must use the dedicated status endpoint");
    }

    private void validateParent(UUID equipmentId, UUID parentId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(equipmentId)) {
            throw RestException.badRequest("Equipment cannot be parent of itself");
        }

        Equipment parent = repository.findByIdAndIsDeletedFalse(parentId)
                .orElseThrow(() -> RestException.notFound("Parent equipment not found: " + parentId));
        UUID currentParentId = parent.getParentId();
        Set<UUID> visited = new HashSet<>();
        while (currentParentId != null) {
            if (!visited.add(currentParentId)) {
                throw RestException.conflict("Circular equipment parent chain detected");
            }
            if (currentParentId.equals(equipmentId)) {
                throw RestException.badRequest("Equipment parent chain cannot be circular");
            }
            UUID finalCurrentParentId = currentParentId;
            Equipment currentParent = repository.findByIdAndIsDeletedFalse(currentParentId)
                    .orElseThrow(() -> RestException.notFound("Parent equipment not found: " + finalCurrentParentId));
            currentParentId = currentParent.getParentId();
        }
    }

    @Transactional(readOnly = true)
    public EquipmentStatsResponse getEquipmentStats(
            String search,
            EquipmentCategory category,
            UUID departmentId,
            UUID equipmentTypeId
    ) {
        String searchPattern = toSearchPattern(search);

        EquipmentStatsProjection stats = repository.getEquipmentStats(
                searchPattern,
                category,
                departmentId,
                equipmentTypeId,
                EquipmentStatus.ACTIVE,
                EquipmentStatus.IN_REPAIR,
                EquipmentStatus.DECOMMISSIONED
        );

        return new EquipmentStatsResponse(
                safe(stats.getTotalInRegistry()),
                safe(stats.getActive()),
                safe(stats.getInRepair()),
                safe(stats.getDecommissioned())
        );
    }

    private String toSearchPattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }

        return "%" + search.trim().toLowerCase() + "%";
    }

    private long safe(Long value) {
        return value == null ? 0L : value;
    }
}
