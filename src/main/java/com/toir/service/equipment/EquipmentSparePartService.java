package com.toir.service.equipment;
import com.toir.entity.equipment.EquipmentSparePart;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.equipment.EquipmentSparePartRepository;

import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.dto.equipmentsparepart.EquipmentSparePartDto;
import com.toir.dto.equipmentsparepart.EquipmentSparePartRequest;
import com.toir.entity.SparePart;
import com.toir.repository.SparePartRepository;
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
public class EquipmentSparePartService {

    private final EquipmentSparePartRepository repo;
    private final EquipmentRepository equipmentRepository;
    private final SparePartRepository sparePartRepository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;

    @Transactional(readOnly = true)
    public List<EquipmentSparePartDto> listForEquipment(UUID equipmentId) {
        if (!equipmentRepository.existsByIdAndIsDeletedFalse(equipmentId)) {
            throw RestException.notFound("Equipment not found: " + equipmentId);
        }
        return repo.findAllByEquipmentIdAndIsDeletedFalse(equipmentId).stream().map(this::enrich).toList();
    }

    @Transactional(readOnly = true)
    public List<EquipmentSparePartDto> listForSparePart(UUID sparePartId) {
        if (!sparePartRepository.existsByIdAndIsDeletedFalse(sparePartId)) {
            throw RestException.notFound("Spare part not found: " + sparePartId);
        }
        return repo.findAllBySparePartIdAndIsDeletedFalse(sparePartId).stream().map(this::enrich).toList();
    }

    public EquipmentSparePartDto add(UUID equipmentId, EquipmentSparePartRequest r) {
        if (!equipmentRepository.existsByIdAndIsDeletedFalse(equipmentId)) {
            throw RestException.notFound("Equipment not found: " + equipmentId);
        }
        SparePart sp = sparePartRepository.findByIdAndIsDeletedFalse(r.sparePartId())
                .orElseThrow(() -> RestException.notFound("Spare part not found: " + r.sparePartId()));
        EquipmentSparePart esp = new EquipmentSparePart();
        esp.setEquipmentId(equipmentId);
        esp.setSparePartId(sp.getId());
        esp.setPosition(r.position());
        esp.setQuantityPerUnit(r.quantityPerUnit());
        esp.setConsumptionRatePerYear(r.consumptionRatePerYear());
        esp.setCriticality(r.criticality());
        esp.setNotes(r.notes());
        EquipmentSparePart saved = repo.save(esp);
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return enrich(saved);
    }

    public EquipmentSparePartDto update(UUID id, EquipmentSparePartRequest r) {
        EquipmentSparePart esp = repo.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Equipment spare part link not found: " + id));
        String oldJson = auditSerializationService.toJson(esp);
        if (!esp.getSparePartId().equals(r.sparePartId())) {
            sparePartRepository.findByIdAndIsDeletedFalse(r.sparePartId())
                    .orElseThrow(() -> RestException.notFound("Spare part not found: " + r.sparePartId()));
            esp.setSparePartId(r.sparePartId());
        }
        esp.setPosition(r.position());
        esp.setQuantityPerUnit(r.quantityPerUnit());
        esp.setConsumptionRatePerYear(r.consumptionRatePerYear());
        esp.setCriticality(r.criticality());
        esp.setNotes(r.notes());
        audit(AuditAction.UPDATE, esp.getId(), oldJson, esp);
        return enrich(esp);
    }

    public void remove(UUID id) {
        EquipmentSparePart esp = repo.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Equipment spare part link not found: " + id));
        String oldJson = auditSerializationService.toJson(esp);
        esp.setDeleted(true);
        EquipmentSparePart saved = repo.save(esp);
        audit(AuditAction.DELETE, saved.getId(), oldJson, null);
    }

    private EquipmentSparePartDto enrich(EquipmentSparePart esp) {
        return sparePartRepository.findByIdAndIsDeletedFalse(esp.getSparePartId())
                .map(sp -> EquipmentSparePartDto.from(esp, sp.getCode(), sp.getName(), sp.getUnit()))
                .orElseGet(() -> EquipmentSparePartDto.from(esp));
    }

    private void audit(AuditAction action, UUID id, String oldJson, EquipmentSparePart current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "equipment_spare_part",
                id != null ? id.toString() : null,
                action,
                AuditModule.EQUIPMENT_SPARE_PART,
                auditMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Запчасть оборудования добавлена";
            case UPDATE -> "Запчасть оборудования обновлена";
            case DELETE -> "Запчасть оборудования удалена";
            default -> "Действие выполнено над запчастью оборудования";
        };
    }
}
