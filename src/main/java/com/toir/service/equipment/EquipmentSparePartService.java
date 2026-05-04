package com.toir.service.equipment;
import com.toir.entity.equipment.EquipmentSparePart;
import com.toir.repository.equipment.EquipmentSparePartRepository;

import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.dto.equipmentsparepart.EquipmentSparePartDto;
import com.toir.dto.equipmentsparepart.EquipmentSparePartRequest;
import com.toir.entity.SparePart;
import com.toir.repository.SparePartRepository;
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
        return enrich(repo.save(esp));
    }

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
        return enrich(esp);
    }

    public void remove(UUID id) {
        EquipmentSparePart esp = repo.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Equipment spare part link not found: " + id));
        esp.setDeleted(true);
        repo.save(esp);
    }

    private EquipmentSparePartDto enrich(EquipmentSparePart esp) {
        return sparePartRepository.findByIdAndIsDeletedFalse(esp.getSparePartId())
                .map(sp -> EquipmentSparePartDto.from(esp, sp.getCode(), sp.getName(), sp.getUnit()))
                .orElseGet(() -> EquipmentSparePartDto.from(esp));
    }
}
