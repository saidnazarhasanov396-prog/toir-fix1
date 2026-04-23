package com.toir.service;
import com.toir.entity.Equipment;
import com.toir.entity.EquipmentNode;
import com.toir.repository.EquipmentNodeRepository;

import com.toir.exception.RestException;
import com.toir.dto.equipmentnode.EquipmentNodeDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class EquipmentNodeService {

    private final EquipmentNodeRepository repository;

    public EquipmentNodeService(EquipmentNodeRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<EquipmentNodeDto> findByEquipment(UUID equipmentId) {
        return repository.findAllByEquipmentId(equipmentId).stream().map(EquipmentNodeDto::from).toList();
    }

    public EquipmentNodeDto create(UUID equipmentId, EquipmentNodeDto r) {
        if (repository.existsByEquipmentIdAndCode(equipmentId, r.code())) {
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
        if (!repository.findAllByParentId(id).isEmpty()) {
            throw RestException.conflict("Node has children");
        }
        repository.delete(getOrThrow(id));
    }

    private EquipmentNode getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> RestException.notFound("Equipment node not found: " + id));
    }
}
