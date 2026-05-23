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

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
        String code = requireText(r.code(), "Node code");
        String serialNumber = normalizeOptional(r.serialNumber());
        if (repository.existsByEquipmentIdAndCodeAndIsDeletedFalse(equipmentId, code)) {
            throw RestException.conflict("Node code already exists in this equipment: " + code);
        }
        validateParent(equipmentId, null, r.parentId());
        validateSerialNumberUnique(equipmentId, serialNumber, null);

        EquipmentNode e = new EquipmentNode();
        e.setEquipmentId(equipmentId);
        e.setParentId(r.parentId());
        e.setCode(code);
        e.setName(requireText(r.name(), "Node name"));
        e.setNodeType(r.nodeType());
        e.setSerialNumber(serialNumber);
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
        String serialNumber = normalizeOptional(r.serialNumber());
        validateParent(e.getEquipmentId(), id, r.parentId());
        validateSerialNumberUnique(e.getEquipmentId(), serialNumber, id);

        e.setParentId(r.parentId());
        e.setName(requireText(r.name(), "Node name"));
        e.setNodeType(r.nodeType());
        e.setSerialNumber(serialNumber);
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
        assertNodeCanBeDeleted(id);
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

    private void validateParent(UUID equipmentId, UUID currentNodeId, UUID parentId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(currentNodeId)) {
            throw RestException.badRequest("Equipment node cannot be parent itself");
        }

        EquipmentNode parent = getOrThrow(parentId);
        if (!Objects.equals(parent.getEquipmentId(), equipmentId)) {
            throw RestException.badRequest("Parent node must belong to the same equipment");
        }

        if (currentNodeId == null) {
            return;
        }

        Set<UUID> visited = new HashSet<>();
        UUID cursor = parentId;
        while (cursor != null) {
            if (cursor.equals(currentNodeId) || !visited.add(cursor)) {
                throw RestException.badRequest("Circular equipment node hierarchy is not allowed");
            }
            EquipmentNode ancestor = getOrThrow(cursor);
            if (!Objects.equals(ancestor.getEquipmentId(), equipmentId)) {
                throw RestException.badRequest("Parent node must belong to the same equipment");
            }
            cursor = ancestor.getParentId();
        }
    }

    private void validateSerialNumberUnique(UUID equipmentId, String serialNumber, UUID currentNodeId) {
        if (serialNumber == null) {
            return;
        }
        boolean exists = currentNodeId == null
                ? repository.existsByEquipmentIdAndSerialNumberAndIsDeletedFalse(equipmentId, serialNumber)
                : repository.existsByEquipmentIdAndSerialNumberAndIdNotAndIsDeletedFalse(equipmentId, serialNumber, currentNodeId);
        if (exists) {
            throw RestException.conflict("Equipment node serial number already exists in this equipment: " + serialNumber);
        }
    }

    private void assertNodeCanBeDeleted(UUID nodeId) {
        if (!repository.findAllByParentIdAndIsDeletedFalse(nodeId).isEmpty()) {
            throw RestException.conflict("Node has children");
        }
        // Future operational references should be checked here: defects, documents, repair history, and work orders.
    }

    private String requireText(String value, String fieldName) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw RestException.badRequest(fieldName + " is required");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
