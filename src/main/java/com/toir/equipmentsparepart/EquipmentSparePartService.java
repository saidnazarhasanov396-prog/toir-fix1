package com.toir.equipmentsparepart;

import com.toir.common.exception.RestException;
import com.toir.equipment.EquipmentRepository;
import com.toir.equipmentsparepart.dto.EquipmentSparePartDto;
import com.toir.equipmentsparepart.dto.EquipmentSparePartRequest;
import com.toir.sparepart.SparePart;
import com.toir.sparepart.SparePartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class EquipmentSparePartService {

    private final EquipmentSparePartRepository repo;
    private final EquipmentRepository equipmentRepository;
    private final SparePartRepository sparePartRepository;

    public EquipmentSparePartService(EquipmentSparePartRepository repo,
                                     EquipmentRepository equipmentRepository,
                                     SparePartRepository sparePartRepository) {
        this.repo = repo;
        this.equipmentRepository = equipmentRepository;
        this.sparePartRepository = sparePartRepository;
    }

    @Transactional(readOnly = true)
    public List<EquipmentSparePartDto> listForEquipment(UUID equipmentId) {
        if (!equipmentRepository.existsById(equipmentId)) {
            throw RestException.notFound("Equipment not found: " + equipmentId);
        }
        return repo.findAllByEquipmentId(equipmentId).stream().map(this::enrich).toList();
    }

    @Transactional(readOnly = true)
    public List<EquipmentSparePartDto> listForSparePart(UUID sparePartId) {
        if (!sparePartRepository.existsById(sparePartId)) {
            throw RestException.notFound("Spare part not found: " + sparePartId);
        }
        return repo.findAllBySparePartId(sparePartId).stream().map(this::enrich).toList();
    }

    public EquipmentSparePartDto add(UUID equipmentId, EquipmentSparePartRequest r) {
        if (!equipmentRepository.existsById(equipmentId)) {
            throw RestException.notFound("Equipment not found: " + equipmentId);
        }
        SparePart sp = sparePartRepository.findById(r.sparePartId())
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
        EquipmentSparePart esp = repo.findById(id)
                .orElseThrow(() -> RestException.notFound("Equipment spare part link not found: " + id));
        if (!esp.getSparePartId().equals(r.sparePartId())) {
            sparePartRepository.findById(r.sparePartId())
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
        EquipmentSparePart esp = repo.findById(id)
                .orElseThrow(() -> RestException.notFound("Equipment spare part link not found: " + id));
        repo.delete(esp);
    }

    private EquipmentSparePartDto enrich(EquipmentSparePart esp) {
        return sparePartRepository.findById(esp.getSparePartId())
                .map(sp -> EquipmentSparePartDto.from(esp, sp.getCode(), sp.getName(), sp.getUnit()))
                .orElseGet(() -> EquipmentSparePartDto.from(esp));
    }
}
