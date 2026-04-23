package com.toir.dto.equipmentpassport;

import com.toir.entity.EquipmentPassport;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record EquipmentPassportDto(
        UUID id,
        @NotNull UUID equipmentId,
        String passportNumber,
        String factoryNumber,
        String manufacturerSerial,
        Double powerKw,
        Double voltageV,
        Double pressureBar,
        Double throughput,
        LocalDate installDate,
        LocalDate lastInspectionDate,
        String notes
) {
    public static EquipmentPassportDto from(EquipmentPassport p) {
        return new EquipmentPassportDto(
                p.getId(), p.getEquipmentId(), p.getPassportNumber(), p.getFactoryNumber(),
                p.getManufacturerSerial(), p.getPowerKw(), p.getVoltageV(), p.getPressureBar(),
                p.getThroughput(), p.getInstallDate(), p.getLastInspectionDate(), p.getNotes()
        );
    }
}
