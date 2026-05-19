package com.toir.service.equipment;

import com.toir.dto.equipmentsparepart.EquipmentSparePartDto;
import com.toir.dto.equipmentsparepart.EquipmentSparePartRequest;
import com.toir.entity.SparePart;
import com.toir.entity.equipment.EquipmentSparePart;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.SparePartRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentSparePartRepository;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EquipmentSparePartService {

    private final EquipmentSparePartRepository repo;
    private final EquipmentRepository equipmentRepository;
    private final SparePartRepository sparePartRepository;
    private final AuditBuilderService auditBuilderService;

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

    @Transactional
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

        auditBuilderService.log(
                "equipment_spare_part",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.EQUIPMENT_SPARE_PART,
                "Запчасть оборудования добавлена",
                null,
                saved
        );
        return enrich(saved);
    }

    @Transactional
    public EquipmentSparePartDto update(UUID id, EquipmentSparePartRequest r) {
        EquipmentSparePart esp = repo.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Equipment spare part link not found: " + id));
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

        EquipmentSparePart save = repo.save(esp);

        auditBuilderService.log(
                "equipment_spare_part",
                save.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.EQUIPMENT_SPARE_PART,
                "Запчасть оборудования обновлена",
                esp,
                save
        );


        return enrich(save);
    }

    @Transactional
    public void remove(UUID id) {
        EquipmentSparePart esp = repo.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Equipment spare part link not found: " + id));
        esp.setDeleted(true);
        EquipmentSparePart saved = repo.save(esp);

        auditBuilderService.log(
                "equipment_spare_part",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.EQUIPMENT_SPARE_PART,
                "Запчасть оборудования удалена",
                saved,
                null
        );

    }

    private EquipmentSparePartDto enrich(EquipmentSparePart esp) {
        return sparePartRepository.findByIdAndIsDeletedFalse(esp.getSparePartId())
                .map(sp -> EquipmentSparePartDto.from(esp, sp.getCode(), sp.getName(), sp.getUnit()))
                .orElseGet(() -> EquipmentSparePartDto.from(esp));
    }

}
