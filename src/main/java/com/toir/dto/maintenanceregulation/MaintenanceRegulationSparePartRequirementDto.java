package com.toir.dto.maintenanceregulation;

import com.toir.entity.maintenance.MaintenanceRegulationSparePartRequirement;
import java.util.UUID;
import java.math.BigDecimal;

public record MaintenanceRegulationSparePartRequirementDto(
        UUID id,
        UUID regulationId,
        UUID sparePartId,
        String sparePartCode,
        String sparePartName,
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.toir.dto.sparepartlifecycle.DecimalStringSerializer.class) BigDecimal quantity,
        String unit,
        String criticality,
        String notes,
        boolean active
) {
    public static MaintenanceRegulationSparePartRequirementDto from(
            MaintenanceRegulationSparePartRequirement requirement
    ) {
        var regulation = requirement.getRegulation();
        var sparePart = requirement.getSparePart();
        return new MaintenanceRegulationSparePartRequirementDto(
                requirement.getId(),
                requirement.getRegulationId() != null
                        ? requirement.getRegulationId()
                        : regulation == null ? null : regulation.getId(),
                requirement.getSparePartId() != null
                        ? requirement.getSparePartId()
                        : sparePart == null ? null : sparePart.getId(),
                sparePart == null ? null : sparePart.getCode(),
                sparePart == null ? null : sparePart.getName(),
                requirement.getQuantity(),
                requirement.getUnit(),
                requirement.getCriticality(),
                requirement.getNotes(),
                requirement.isActive()
        );
    }
}
