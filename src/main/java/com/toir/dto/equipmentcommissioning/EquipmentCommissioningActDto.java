package com.toir.dto.equipmentcommissioning;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.entity.equipment.EquipmentCommissioningAct;
import com.toir.enums.EquipmentCommissioningStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EquipmentCommissioningActDto(
        UUID id,
        UUID equipmentId,
        UUID sourceWarehouseId,
        UUID warehouseItemId,
        UUID targetDepartmentId,
        UUID targetLocationId,
        UUID responsibleEmployeeId,
        String actNumber,
        LocalDate actDate,
        LocalDate commissionedAt,
        LocalDate operationStartDate,
        List<EquipmentCommissioningActRequest.Signatory> committee,
        String notes,
        EquipmentCommissioningStatus status,
        UUID approvalRequestId,
        Instant submittedAt,
        Instant approvedAt,
        UUID approvedBy,
        Instant rejectedAt,
        String rejectionReason,
        UUID warehouseMovementId
) {
    public static EquipmentCommissioningActDto from(EquipmentCommissioningAct act, ObjectMapper mapper) {
        return new EquipmentCommissioningActDto(
                act.getId(), act.getEquipmentId(), act.getSourceWarehouseId(), act.getWarehouseItemId(),
                act.getTargetDepartmentId(), act.getTargetLocationId(), act.getResponsibleEmployeeId(),
                act.getActNumber(), act.getActDate(), act.getCommissionedAt(), act.getOperationStartDate(),
                committee(act.getCommitteeJson(), mapper), act.getNotes(), act.getStatus(),
                act.getApprovalRequestId(), act.getSubmittedAt(), act.getApprovedAt(), act.getApprovedBy(),
                act.getRejectedAt(), act.getRejectionReason(), act.getWarehouseMovementId()
        );
    }

    private static List<EquipmentCommissioningActRequest.Signatory> committee(String json, ObjectMapper mapper) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
