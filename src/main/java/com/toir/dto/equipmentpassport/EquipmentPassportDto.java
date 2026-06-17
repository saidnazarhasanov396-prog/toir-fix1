package com.toir.dto.equipmentpassport;

import com.toir.entity.equipment.EquipmentPassport;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
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
        List<ProductivityEntryDto> productivity,
        LocalDate installDate,
        LocalDate lastInspectionDate,
        String notes
) {
    public static EquipmentPassportDto from(EquipmentPassport p) {
        return new EquipmentPassportDto(
                p.getId(), p.getEquipmentId(), p.getPassportNumber(), p.getFactoryNumber(),
                p.getManufacturerSerial(), p.getPowerKw(), p.getVoltageV(), p.getPressureBar(),
                p.getProductivity() != null ? p.getProductivity() : java.util.List.of(),
                p.getInstallDate(), p.getLastInspectionDate(), p.getNotes()
        );
    }
}
