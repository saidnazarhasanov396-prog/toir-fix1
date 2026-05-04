package com.toir.service.equipment;
import com.toir.entity.Department;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentPassport;
import com.toir.enums.AuditAction;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.entity.equipment.EquipmentType;
import com.toir.entity.Location;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentPassportRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentTypeRepository;
import com.toir.repository.LocationRepository;

import com.toir.exception.RestException;
import com.toir.dto.equipment.EquipmentDto;
import com.toir.dto.equipment.EquipmentRequest;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class EquipmentService {

    private final EquipmentRepository repository;
    private final DepartmentRepository departmentRepository;
    private final LocationRepository locationRepository;
    private final EquipmentTypeRepository equipmentTypeRepository;
    private final EquipmentPassportRepository passportRepository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;


    @Transactional(readOnly = true)
    public Page<EquipmentDto> search(UUID departmentId, UUID equipmentTypeId, EquipmentStatus status, EquipmentCategory category, String search, int page, int pageSize) {
        Page<Equipment> items = repository.search(departmentId, equipmentTypeId, status, category, search, PaginationUtils.pageRequest(page, pageSize));
        return enrich(items);
    }

    @Transactional(readOnly = true)
    public EquipmentDto findById(UUID id) {
        return enrich(List.of(getOrThrow(id))).getFirst();
    }

    @Transactional
    public EquipmentDto create(EquipmentRequest request) {
        if (repository.existsByInventoryNumberAndIsDeletedFalse(request.inventoryNumber())) {
            throw RestException.conflict("Inventory number already exists: " + request.inventoryNumber());
        }
        Equipment entity = new Equipment();
        entity.setCode(nextCode());
        apply(entity, request);
        Equipment saved = repository.save(entity);

        String newJson = auditSerializationService.toJson(saved);
        auditBuilderService.log(
                "equipment",
                saved.getId().toString(),
                com.toir.enums.AuditAction.CREATE,
                com.toir.enums.AuditModule.EQUIPMENT,
                "Оборудование создано: код=%s, наименование=%s".formatted(saved.getCode(), saved.getName()),
                null,
                newJson
        );
        return enrich(List.of(saved)).getFirst();
    }

    @Transactional
    public EquipmentDto update(UUID id, EquipmentRequest request) {
        Equipment entity = getOrThrow(id);

        String oldJson = auditSerializationService.toJson(entity);

        applyForUpdate(entity, request);

        Equipment saved = repository.save(entity);
        String newJson = auditSerializationService.toJson(saved);
        auditBuilderService.log(
                "equipment",
                saved.getId().toString(),
                AuditAction.UPDATE,
                com.toir.enums.AuditModule.EQUIPMENT,
                "Оборудование обновлено: код=%s, наименование=%s".formatted(saved.getCode(), saved.getName()),
                oldJson,
                newJson);
        return enrich(List.of(entity)).getFirst();
    }

    @Transactional
    public void delete(UUID id) {
        Equipment entity = getOrThrow(id);
        if (!repository.findAllByParentIdAndIsDeletedFalse(id).isEmpty()) {
            throw RestException.conflict("Equipment has child nodes");
        }
        String oldJson = auditSerializationService.toJson(entity);

        entity.setDeleted(true);
        Equipment saved = repository.save(entity);

        auditBuilderService.log(
                "equipment",
                saved.getId().toString(),
                AuditAction.DELETE,
                com.toir.enums.AuditModule.EQUIPMENT,
                "Оборудование удалено: код=%s, наименование=%s".formatted(saved.getCode(), saved.getName()),
                oldJson,
                null);
    }

    Equipment getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + id));
    }

    Page<EquipmentDto> enrich(Page<Equipment> items) {
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
        entity.setParentId(request.parentId() != null ? request.parentId() : entity.getParentId());
        entity.setCriticalityClassId(request.criticalityClassId()  != null ? request.criticalityClassId() : entity.getCriticalityClassId());
        entity.setResponsibleId(request.responsibleId() != null ? request.responsibleId() : entity.getResponsibleId());
        entity.setManufacturer(request.manufacturer() != null ? request.manufacturer() : entity.getManufacturer());
        if (request.status() != null) entity.setStatus(request.status());
        entity.setCategory(request.category() != null ? request.category() : entity.getCategory());
        entity.setCommissionedAt(request.commissionedAt() != null ? request.commissionedAt() : entity.getCommissionedAt());
        entity.setWarrantyUntil(request.warrantyUntil() != null ? request.warrantyUntil() : entity.getWarrantyUntil());
        entity.setDescription(request.description() != null ? request.description() : entity.getDescription());
    }
}
