package com.toir.service.equipment;
import com.toir.entity.equipment.EquipmentPassport;
import com.toir.repository.equipment.EquipmentPassportRepository;

import com.toir.exception.RestException;
import com.toir.dto.equipmentpassport.EquipmentPassportDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EquipmentPassportService {

    private final EquipmentPassportRepository repository;


    @Transactional(readOnly = true)
    public EquipmentPassportDto findByEquipment(UUID equipmentId) {
        return EquipmentPassportDto.from(repository.findByEquipmentIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Passport not found for equipment: " + equipmentId)));
    }

    public EquipmentPassportDto upsert(UUID equipmentId, EquipmentPassportDto r) {
        EquipmentPassport p = repository.findByEquipmentIdAndIsDeletedFalse(equipmentId).orElseGet(EquipmentPassport::new);
        p.setEquipmentId(equipmentId);
        if (r.passportNumber() != null && !r.passportNumber().equals(p.getPassportNumber())
                && repository.existsByPassportNumberAndIsDeletedFalse(r.passportNumber())) {
            throw RestException.conflict("Passport number already exists: " + r.passportNumber());
        }
        p.setPassportNumber(r.passportNumber());
        p.setFactoryNumber(r.factoryNumber());
        p.setManufacturerSerial(r.manufacturerSerial());
        p.setPowerKw(r.powerKw());
        p.setVoltageV(r.voltageV());
        p.setPressureBar(r.pressureBar());
        p.setThroughput(r.throughput());
        p.setInstallDate(r.installDate());
        p.setLastInspectionDate(r.lastInspectionDate());
        p.setNotes(r.notes());
        return EquipmentPassportDto.from(repository.save(p));
    }
}
