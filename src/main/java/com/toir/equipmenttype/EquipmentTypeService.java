package com.toir.equipmenttype;

import com.toir.common.exception.RestException;
import com.toir.equipmenttype.dto.EquipmentTypeDto;
import com.toir.equipmenttype.dto.EquipmentTypeRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class EquipmentTypeService {

    private final EquipmentTypeRepository repository;

    public EquipmentTypeService(EquipmentTypeRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<EquipmentTypeDto> findAll() {
        return repository.findAll().stream().map(EquipmentTypeDto::from).toList();
    }

    @Transactional(readOnly = true)
    public EquipmentTypeDto findById(UUID id) {
        return EquipmentTypeDto.from(getOrThrow(id));
    }

    public EquipmentType getEntityOrThrow(UUID id) {
        return getOrThrow(id);
    }

    public EquipmentTypeDto create(EquipmentTypeRequest request) {
        if (repository.existsByCode(request.code())) {
            throw RestException.conflict("Equipment type code already exists: " + request.code());
        }
        EquipmentType entity = new EquipmentType();
        apply(entity, request);
        return EquipmentTypeDto.from(repository.save(entity));
    }

    public EquipmentTypeDto update(UUID id, EquipmentTypeRequest request) {
        EquipmentType entity = getOrThrow(id);
        apply(entity, request);
        return EquipmentTypeDto.from(entity);
    }

    public void delete(UUID id) {
        repository.delete(getOrThrow(id));
    }

    private EquipmentType getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> RestException.notFound("Equipment type not found: " + id));
    }

    private void apply(EquipmentType entity, EquipmentTypeRequest request) {
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setCategory(request.category());
        entity.setDescription(request.description());
    }
}
