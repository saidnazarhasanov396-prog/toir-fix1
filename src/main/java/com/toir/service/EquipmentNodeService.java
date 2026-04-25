package com.toir.service;
import com.toir.entity.Equipment;
import com.toir.entity.EquipmentNode;
import com.toir.repository.EquipmentNodeRepository;

import com.toir.exception.RestException;
import com.toir.dto.equipmentnode.EquipmentNodeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EquipmentNodeService {

    private final EquipmentNodeRepository repository;

    @Transactional(readOnly = true)
    public List<EquipmentNodeDto> findByEquipment(UUID equipmentId) {
        return repository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId).stream().map(EquipmentNodeDto::from).toList();
    }

    public EquipmentNodeDto create(UUID equipmentId, EquipmentNodeDto r) {
        if (repository.existsByEquipmentIdAndCodeAndIsDeletedFalse(equipmentId, r.code())) {
            throw RestException.conflict("Node code already exists in this equipment: " + r.code());
        }
        EquipmentNode e = new EquipmentNode();
        e.setEquipmentId(equipmentId);
        e.setParentId(r.parentId());
        e.setCode(r.code());
        e.setName(r.name());
        e.setNodeType(r.nodeType());
        e.setSerialNumber(r.serialNumber());
        e.setDescription(r.description());
        return EquipmentNodeDto.from(repository.save(e));
    }

    public EquipmentNodeDto update(UUID id, EquipmentNodeDto r) {
        EquipmentNode e = getOrThrow(id);
        e.setParentId(r.parentId());
        e.setName(r.name());
        e.setNodeType(r.nodeType());
        e.setSerialNumber(r.serialNumber());
        e.setDescription(r.description());
        return EquipmentNodeDto.from(e);
    }

    public void delete(UUID id) {
        if (!repository.findAllByParentIdAndIsDeletedFalse(id).isEmpty()) {
            throw RestException.conflict("Node has children");
        }
        var entity = getOrThrow(id);
        entity.setDeleted(true);
        repository.save(entity);
    }

    private EquipmentNode getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Equipment node not found: " + id));
    }
}
