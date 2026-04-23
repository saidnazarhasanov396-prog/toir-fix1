package com.toir.dto.equipmentkpi;

import com.toir.entity.EquipmentKPI;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record EquipmentKPIDto(
        UUID id,
        @NotNull UUID equipmentId,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        Double operatingHours,
        Double downtimeHours,
        Integer failureCount,
        Integer repairCount,
        Double mtbfHours,
        Double mttrHours,
        Double availability
) {
    public static EquipmentKPIDto from(EquipmentKPI k) {
        return new EquipmentKPIDto(k.getId(), k.getEquipmentId(), k.getPeriodStart(), k.getPeriodEnd(),
                k.getOperatingHours(), k.getDowntimeHours(), k.getFailureCount(), k.getRepairCount(),
                k.getMtbfHours(), k.getMttrHours(), k.getAvailability());
    }
}
