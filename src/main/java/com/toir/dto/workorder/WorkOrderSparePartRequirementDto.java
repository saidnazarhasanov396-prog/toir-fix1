package com.toir.dto.workorder;

import com.toir.entity.maintenance.WorkOrderSparePartRequirement;
import com.toir.enums.WorkOrderSparePartRequirementSourceType;
import com.toir.enums.WorkOrderSparePartRequirementStatus;
import java.util.UUID;
import java.math.BigDecimal;

public record WorkOrderSparePartRequirementDto(
        UUID id,
        UUID workOrderId,
        WorkOrderSparePartRequirementSourceType sourceType,
        UUID sourceRequirementId,
        UUID templateId,
        UUID operationId,
        UUID campaignRequirementId,
        UUID warehouseId,
        String operationName,
        UUID sparePartId,
        String sparePartCode,
        String sparePartName,
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.toir.dto.sparepartlifecycle.DecimalStringSerializer.class)
        BigDecimal requiredQty,
        String unit,
        String criticality,
        String notes,
        WorkOrderSparePartRequirementStatus status,
        // Faktik bajarilish ma'lumotlari
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.toir.dto.sparepartlifecycle.DecimalStringSerializer.class) BigDecimal issuedQty,
        UUID issuedSparePartId,
        String issuedSparePartCode,
        String issuedSparePartName,
        boolean isReplacement
) {
    public static WorkOrderSparePartRequirementDto from(WorkOrderSparePartRequirement requirement) {
        var workOrder = requirement.getWorkOrder();
        var sourceRequirement = requirement.getSourceRequirement();
        var regulationRequirement = requirement.getRegulationRequirement();
        var template = requirement.getTemplate();
        var operation = requirement.getOperation();
        var sparePart = requirement.getSparePart();
        return new WorkOrderSparePartRequirementDto(
                requirement.getId(),
                requirement.getWorkOrderId() != null
                        ? requirement.getWorkOrderId()
                        : workOrder == null ? null : workOrder.getId(),
                requirement.getSourceType(),
                sourceRequirementId(requirement),
                requirement.getTemplateId() != null
                        ? requirement.getTemplateId()
                        : template == null ? null : template.getId(),
                requirement.getOperationId() != null
                        ? requirement.getOperationId()
                        : operation == null ? null : operation.getId(),
                requirement.getCampaignRequirementId(),
                requirement.getWarehouseId(),
                operation == null ? null : operation.getName(),
                requirement.getSparePartId() != null
                        ? requirement.getSparePartId()
                        : sparePart == null ? null : sparePart.getId(),
                sparePart == null ? null : sparePart.getCode(),
                sparePart == null ? null : sparePart.getName(),
                requirement.getRequiredQty(),
                requirement.getUnit(),
                requirement.getCriticality(),
                requirement.getNotes(),
                requirement.getStatus(),
                null, null, null, null, false
        );
    }

    public static WorkOrderSparePartRequirementDto withUsage(
            WorkOrderSparePartRequirement requirement,
            BigDecimal issuedQty,
            UUID issuedSparePartId,
            String issuedSparePartCode,
            String issuedSparePartName
    ) {
        WorkOrderSparePartRequirementDto base = from(requirement);
        boolean isReplacement = issuedSparePartId != null
                && !issuedSparePartId.equals(requirement.getSparePartId());
        return new WorkOrderSparePartRequirementDto(
                base.id(), base.workOrderId(), base.sourceType(), base.sourceRequirementId(),
                base.templateId(), base.operationId(), base.campaignRequirementId(), base.warehouseId(), base.operationName(),
                base.sparePartId(), base.sparePartCode(), base.sparePartName(),
                base.requiredQty(), base.unit(), base.criticality(), base.notes(), base.status(),
                issuedQty, issuedSparePartId, issuedSparePartCode, issuedSparePartName, isReplacement
        );
    }

    private static UUID sourceRequirementId(WorkOrderSparePartRequirement requirement) {
        if (requirement.getSourceRequirementId() != null) {
            return requirement.getSourceRequirementId();
        }
        if (requirement.getSourceRequirement() != null) {
            return requirement.getSourceRequirement().getId();
        }
        if (requirement.getRegulationRequirementId() != null) {
            return requirement.getRegulationRequirementId();
        }
        return requirement.getRegulationRequirement() == null ? null : requirement.getRegulationRequirement().getId();
    }
}
