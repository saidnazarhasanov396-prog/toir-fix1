package com.toir.service.equipment;

import com.toir.dto.equipment.EquipmentDto;
import com.toir.dto.equipment.EquipmentRequest;
import com.toir.entity.Department;
import com.toir.entity.Location;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentPassport;
import com.toir.entity.equipment.EquipmentType;
import com.toir.enums.AuditAction;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.WarehouseEquipmentStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkType;
import com.toir.exception.RestException;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.LocationRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentPassportRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentTypeRepository;
import com.toir.util.AuditBuilderService;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
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
    private final AuditBuilderService auditBuilderService;
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
    public Page<EquipmentDto> findChildren(UUID parentId, int page, int pageSize) {
        getOrThrow(parentId);
        return enrich(repository.findAllByParentIdAndIsDeletedFalse(parentId, PaginationUtils.pageRequest(page, pageSize)));
    }

    @Transactional
    public EquipmentDto create(EquipmentRequest request) {
        validateClientProvidedCode(request.code());
        if (repository.existsByInventoryNumberAndIsDeletedFalse(request.inventoryNumber())) {
            throw RestException.conflict("Inventory number already exists: " + request.inventoryNumber());
        }
        validateParent(null, request.parentId());
        Equipment entity = new Equipment();
        entity.setCode(nextCode());
        apply(entity, request);
        Equipment saved = repository.save(entity);

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
    public EquipmentDto update(UUID id, EquipmentRequest request) {
        Equipment entity = getOrThrow(id);
        validateClientProvidedCode(request.code());


        applyForUpdate(entity, request);
        validateParent(entity.getId(), entity.getParentId());

        Equipment saved = repository.save(entity);

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
        Map<UUID, EquipmentType> typeMap = byId(equipmentTypeRepository.findAllByIdInAndIsDeletedFalse(typeIds), EquipmentType::getId);
        Map<UUID, Equipment> parentMap = byId(repository.findAllByIdInAndIsDeletedFalse(parentIds), Equipment::getId);
        Map<UUID, EquipmentPassport> passportMap = passportRepository
                .findAllByEquipmentIdInAndIsDeletedFalse(equipmentIds).stream()
                .collect(Collectors.toMap(EquipmentPassport::getEquipmentId, Function.identity(), (a, b) -> a));

        return items.stream()
                .map(e -> EquipmentDto.from(
                        e,
                        deptRef(deptMap.get(e.getDepartmentId())),
                        locRef(locMap.get(e.getLocationId())),
                        typeRef(typeMap.get(e.getEquipmentTypeId())),
                        parentRef(parentMap.get(e.getParentId())),
                        passportRef(passportMap.get(e.getId()))))
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

    private void apply(Equipment entity, EquipmentRequest request) {
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
        entity.setDescription(request.description());
    }

    private void validateClientProvidedCode(String code) {
        if (code != null && !code.isBlank()) {
            throw RestException.badRequest("Equipment code is generated by system and must not be provided");
        }
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

    private void applyForUpdate(Equipment entity, EquipmentRequest request) {
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
        if (request.status() != null) entity.setStatus(request.status());
        entity.setCategory(request.category() != null ? request.category() : entity.getCategory());
        entity.setCommissionedAt(request.commissionedAt() != null ? request.commissionedAt() : entity.getCommissionedAt());
        entity.setWarrantyUntil(request.warrantyUntil() != null ? request.warrantyUntil() : entity.getWarrantyUntil());
        entity.setDescription(request.description() != null ? request.description() : entity.getDescription());
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
}
