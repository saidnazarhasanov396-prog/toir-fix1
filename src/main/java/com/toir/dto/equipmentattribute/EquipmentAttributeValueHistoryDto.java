package com.toir.dto.equipmentattribute;

import com.toir.entity.equipment.EquipmentAttributeValueHistory;
import com.toir.enums.EquipmentAttributeValueHistorySource;

import java.time.Instant;
import java.util.UUID;

public record EquipmentAttributeValueHistoryDto(
        UUID id,
        UUID equipmentId,
        UUID attributeDefinitionId,
        String attributeKey,
        String attributeLabel,
        String oldValue,
        String newValue,
        UUID changedBy,
        Instant changedAt,
        EquipmentAttributeValueHistorySource source,
        String reason
) {
    public static EquipmentAttributeValueHistoryDto from(EquipmentAttributeValueHistory history) {
        return new EquipmentAttributeValueHistoryDto(
                history.getId(),
                history.getEquipmentId(),
                history.getAttributeDefinitionId(),
                history.getAttributeKey(),
                history.getAttributeLabel(),
                history.getOldValue(),
                history.getNewValue(),
                history.getChangedBy(),
                history.getChangedAt(),
                history.getSource(),
                history.getReason()
        );
    }
}
