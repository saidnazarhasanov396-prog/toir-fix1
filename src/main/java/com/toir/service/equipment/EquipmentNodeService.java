package com.toir.service.equipment;

import com.toir.dto.equipmentnode.EquipmentNodeDto;
import com.toir.entity.equipment.EquipmentNode;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentNodeRepository;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EquipmentNodeService {

    private final EquipmentNodeRepository repository;
    private final AuditBuilderService auditBuilderService;

    @Transactional(readOnly = true)
    public List<EquipmentNodeDto> findByEquipment(UUID equipmentId) {
        return repository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId).stream().map(EquipmentNodeDto::from).toList();
    }

    @Transactional
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
        EquipmentNode created = repository.save(e);

        auditBuilderService.log(
                "equipment_node",
                created.getId().toString(),
                AuditAction.CREATE,
                AuditModule.EQUIPMENT_NODE,
                "Узел оборудования создан",
                null,
                created
        );

        return EquipmentNodeDto.from(created);
    }

    @Transactional
    public EquipmentNodeDto update(UUID id, EquipmentNodeDto r) {
        EquipmentNode e = getOrThrow(id);
        e.setParentId(r.parentId());
        e.setName(r.name());
        e.setNodeType(r.nodeType());
        e.setSerialNumber(r.serialNumber());
        e.setDescription(r.description());

        EquipmentNode updated = repository.save(e);

        auditBuilderService.log(
                "equipment_node",
                updated.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.EQUIPMENT_NODE,
                "Узел оборудования обновлен",
                e,
                updated
        );

        return EquipmentNodeDto.from(updated);
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.findAllByParentIdAndIsDeletedFalse(id).isEmpty()) {
            throw RestException.conflict("Node has children");
        }
        var entity = getOrThrow(id);
        entity.setDeleted(true);
        EquipmentNode deleted = repository.save(entity);

        auditBuilderService.log(
                "equipment_node",
                deleted.getId().toString(),
                AuditAction.DELETE,
                AuditModule.EQUIPMENT_NODE,
                "Узел оборудования удален",
                deleted,
                null
        );

    }

    private EquipmentNode getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Equipment node not found: " + id));
    }
}
