package com.toir.service;
import com.toir.entity.Equipment;
import com.toir.entity.EquipmentType;
import com.toir.repository.EquipmentTypeRepository;

import com.toir.exception.RestException;
import com.toir.dto.equipmenttype.EquipmentTypeDto;
import com.toir.dto.equipmenttype.EquipmentTypeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EquipmentTypeService {

    private final EquipmentTypeRepository repository;


    @Transactional(readOnly = true)
    public List<EquipmentTypeDto> findAll() {
        return repository.findAllByIsDeletedFalse().stream().map(EquipmentTypeDto::from).toList();
    }

    @Transactional(readOnly = true)
    public EquipmentTypeDto findById(UUID id) {
        return EquipmentTypeDto.from(getOrThrow(id));
    }

    public EquipmentType getEntityOrThrow(UUID id) {
        return getOrThrow(id);
    }

    public EquipmentTypeDto create(EquipmentTypeRequest request) {
        if (repository.existsByCodeAndIsDeletedFalse(request.code())) {
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
        var entity = getOrThrow(id);
        entity.setDeleted(true);
        repository.save(entity);
    }

    private EquipmentType getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Equipment type not found: " + id));
    }

    private void apply(EquipmentType entity, EquipmentTypeRequest request) {
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setCategory(request.category());
        entity.setDescription(request.description());
    }
}
