package com.toir.equipment;

import com.toir.common.exception.RestException;
import com.toir.equipment.dto.EquipmentDto;
import com.toir.equipment.dto.EquipmentRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class EquipmentService {

    private final EquipmentRepository repository;

    public EquipmentService(EquipmentRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<EquipmentDto> search(UUID departmentId, UUID equipmentTypeId, EquipmentStatus status) {
        return repository.search(departmentId, equipmentTypeId, status).stream()
                .map(EquipmentDto::from).toList();
    }

    @Transactional(readOnly = true)
    public EquipmentDto findById(UUID id) {
        return EquipmentDto.from(getOrThrow(id));
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
        return EquipmentDto.from(repository.save(entity));
    }

    public EquipmentDto update(UUID id, EquipmentRequest request) {
        Equipment entity = getOrThrow(id);
        apply(entity, request);
        return EquipmentDto.from(entity);
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
