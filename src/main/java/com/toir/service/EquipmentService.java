package com.toir.service;
import com.toir.entity.Department;
import com.toir.entity.Equipment;
import com.toir.entity.EquipmentPassport;
import com.toir.enums.EquipmentStatus;
import com.toir.entity.EquipmentType;
import com.toir.entity.Location;
import com.toir.repository.DepartmentRepository;
import com.toir.repository.EquipmentPassportRepository;
import com.toir.repository.EquipmentRepository;
import com.toir.repository.EquipmentTypeRepository;
import com.toir.repository.LocationRepository;

import com.toir.exception.RestException;
import com.toir.dto.equipment.EquipmentDto;
import com.toir.dto.equipment.EquipmentRequest;
import lombok.RequiredArgsConstructor;
<<<<<<< HEAD
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
=======
>>>>>>> f315bbd (service @Transactional deleted , added @RequiredArgConstructor)
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
@RequiredArgsConstructor
public class EquipmentService {

    private final EquipmentRepository repository;
    private final DepartmentRepository departmentRepository;
    private final LocationRepository locationRepository;
    private final EquipmentTypeRepository equipmentTypeRepository;
    private final EquipmentPassportRepository passportRepository;


    @Transactional(readOnly = true)
    public Page<EquipmentDto> search(UUID departmentId, UUID equipmentTypeId, EquipmentStatus status, String search, int page, int pageSize) {
        Page<Equipment> items = repository.search(departmentId, equipmentTypeId, status, search, PageRequest.of(page, pageSize));
        
        if (items.isEmpty()) return items.map(e -> null); // should not hit the null because it's empty

        Set<UUID> deptIds = collectIds(items.getContent(), Equipment::getDepartmentId);
        Set<UUID> locIds = collectIds(items.getContent(), Equipment::getLocationId);
        Set<UUID> typeIds = collectIds(items.getContent(), Equipment::getEquipmentTypeId);
        Set<UUID> parentIds = collectIds(items.getContent(), Equipment::getParentId);
        Set<UUID> equipmentIds = items.getContent().stream().map(Equipment::getId).collect(Collectors.toSet());

        Map<UUID, Department> deptMap = byId(departmentRepository.findAllById(deptIds), Department::getId);
        Map<UUID, Location> locMap = byId(locationRepository.findAllById(locIds), Location::getId);
        Map<UUID, EquipmentType> typeMap = byId(equipmentTypeRepository.findAllById(typeIds), EquipmentType::getId);
        Map<UUID, Equipment> parentMap = byId(repository.findAllById(parentIds), Equipment::getId);
        Map<UUID, EquipmentPassport> passportMap = passportRepository
                .findAllByEquipmentIdIn(equipmentIds).stream()
                .collect(Collectors.toMap(EquipmentPassport::getEquipmentId, Function.identity(), (a, b) -> a));

        return items.map(e -> EquipmentDto.from(
                e,
                deptRef(deptMap.get(e.getDepartmentId())),
                locRef(locMap.get(e.getLocationId())),
                typeRef(typeMap.get(e.getEquipmentTypeId())),
                parentRef(parentMap.get(e.getParentId())),
                passportRef(passportMap.get(e.getId()))));
    }

    @Transactional(readOnly = true)
    public EquipmentDto findById(UUID id) {
        return enrich(List.of(getOrThrow(id))).get(0);
    }

    public EquipmentDto create(EquipmentRequest request) {
        if (repository.existsByCode(request.code())) {
            throw RestException.conflict("Equipment code already exists: " + request.code());
        }
        if (repository.existsByInventoryNumber(request.inventoryNumber())) {
            throw RestException.conflict("Inventory number already exists: " + request.inventoryNumber());
        }
        Equipment entity = new Equipment();
        apply(entity, request);
        Equipment saved = repository.save(entity);
        return enrich(List.of(saved)).get(0);
    }

    public EquipmentDto update(UUID id, EquipmentRequest request) {
        Equipment entity = getOrThrow(id);
        apply(entity, request);
        return enrich(List.of(entity)).get(0);
    }

    public void delete(UUID id) {
        Equipment entity = getOrThrow(id);
        if (!repository.findAllByParentId(id).isEmpty()) {
            throw RestException.conflict("Equipment has child nodes");
        }
        repository.delete(entity);
    }

    Equipment getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + id));
    }

    private List<EquipmentDto> enrich(List<Equipment> items) {
        if (items.isEmpty()) return Collections.emptyList();

        Set<UUID> deptIds = collectIds(items, Equipment::getDepartmentId);
        Set<UUID> locIds = collectIds(items, Equipment::getLocationId);
        Set<UUID> typeIds = collectIds(items, Equipment::getEquipmentTypeId);
        Set<UUID> parentIds = collectIds(items, Equipment::getParentId);
        Set<UUID> equipmentIds = items.stream().map(Equipment::getId).collect(Collectors.toSet());

        Map<UUID, Department> deptMap = byId(departmentRepository.findAllById(deptIds), Department::getId);
        Map<UUID, Location> locMap = byId(locationRepository.findAllById(locIds), Location::getId);
        Map<UUID, EquipmentType> typeMap = byId(equipmentTypeRepository.findAllById(typeIds), EquipmentType::getId);
        Map<UUID, Equipment> parentMap = byId(repository.findAllById(parentIds), Equipment::getId);
        Map<UUID, EquipmentPassport> passportMap = passportRepository
                .findAllByEquipmentIdIn(equipmentIds).stream()
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
        entity.setCode(request.code());
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
        entity.setCommissionedAt(request.commissionedAt());
        entity.setWarrantyUntil(request.warrantyUntil());
        entity.setDescription(request.description());
    }
}
