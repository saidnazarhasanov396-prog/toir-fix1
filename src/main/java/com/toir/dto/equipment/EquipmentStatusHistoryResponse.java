package com.toir.dto.equipment;

import com.toir.entity.equipment.EquipmentStatusHistory;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.EquipmentStatusSource;

import java.time.Instant;
import java.util.UUID;

public record EquipmentStatusHistoryResponse(
        UUID id,
        UUID equipmentId,
        EquipmentStatus fromStatus,
        EquipmentStatus toStatus,
        String reason,
        EquipmentStatusSource source,
        UUID changedBy,
        Instant changedAt,
        String relatedEntityType,
        UUID relatedEntityId
) {
    public static EquipmentStatusHistoryResponse from(EquipmentStatusHistory history) {
        return new EquipmentStatusHistoryResponse(
                history.getId(),
                history.getEquipmentId(),
                history.getFromStatus(),
                history.getToStatus(),
                history.getReason(),
                history.getSource(),
                history.getChangedBy(),
                history.getChangedAt(),
                history.getRelatedEntityType(),
                history.getRelatedEntityId()
        );
    }
}
