package com.toir.dto.maintenancetemplate;

import com.toir.entity.maintenance.MaintenanceTemplateSparePartRequirement;

import java.util.UUID;

public record MaintenanceTemplateSparePartRequirementDto(
        UUID id,
        UUID templateId,
        UUID operationId,
        String operationName,
        UUID sparePartId,
        String sparePartCode,
        String sparePartName,
        double quantity,
        String unit,
        String criticality,
        String notes,
        boolean active
) {
    public static MaintenanceTemplateSparePartRequirementDto from(MaintenanceTemplateSparePartRequirement requirement) {
        var template = requirement.getTemplate();
        var operation = requirement.getOperation();
        var sparePart = requirement.getSparePart();
        return new MaintenanceTemplateSparePartRequirementDto(
                requirement.getId(),
                requirement.getTemplateId() != null ? requirement.getTemplateId() : template == null ? null : template.getId(),
                requirement.getOperationId() != null ? requirement.getOperationId() : operation == null ? null : operation.getId(),
                operation == null ? null : operation.getName(),
                requirement.getSparePartId() != null ? requirement.getSparePartId() : sparePart == null ? null : sparePart.getId(),
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
