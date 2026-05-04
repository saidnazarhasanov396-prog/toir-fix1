package com.toir.service.equipment;
import com.toir.entity.equipment.EquipmentNode;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.equipment.EquipmentNodeRepository;

import com.toir.exception.RestException;
import com.toir.dto.equipmentnode.EquipmentNodeDto;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class EquipmentNodeService {

    private final EquipmentNodeRepository repository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;

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
        EquipmentNode saved = repository.save(e);
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return EquipmentNodeDto.from(saved);
    }

    public EquipmentNodeDto update(UUID id, EquipmentNodeDto r) {
        EquipmentNode e = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(e);
        e.setParentId(r.parentId());
        e.setName(r.name());
        e.setNodeType(r.nodeType());
        e.setSerialNumber(r.serialNumber());
        e.setDescription(r.description());
        audit(AuditAction.UPDATE, e.getId(), oldJson, e);
        return EquipmentNodeDto.from(e);
    }

    public void delete(UUID id) {
        if (!repository.findAllByParentIdAndIsDeletedFalse(id).isEmpty()) {
            throw RestException.conflict("Node has children");
        }
        var entity = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(entity);
        entity.setDeleted(true);
        EquipmentNode saved = repository.save(entity);
        audit(AuditAction.DELETE, saved.getId(), oldJson, null);
    }

    private EquipmentNode getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Equipment node not found: " + id));
    }

    private void audit(AuditAction action, UUID id, String oldJson, EquipmentNode current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "equipment_node",
                id != null ? id.toString() : null,
                action,
                AuditModule.EQUIPMENT_NODE,
                auditMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Узел оборудования создан";
            case UPDATE -> "Узел оборудования обновлен";
            case DELETE -> "Узел оборудования удален";
            default -> "Действие выполнено над узлом оборудования";
        };
    }
}
