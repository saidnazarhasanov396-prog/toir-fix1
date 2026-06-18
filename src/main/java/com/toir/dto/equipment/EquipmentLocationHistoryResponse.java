package com.toir.dto.equipment;

import com.toir.entity.equipment.EquipmentLocationHistory;
import com.toir.enums.EquipmentLocationType;
import com.toir.enums.EquipmentOutsideReason;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EquipmentLocationHistoryResponse(
        UUID id,
        UUID equipmentId,
        LocationSnapshot from,
        LocationSnapshot to,
        UUID responsibleDepartmentId,
        UUID changedBy,
        Instant changedAt,
        Instant activeUntil,
        Long durationMinutes,
        String note
) {
    public record LocationSnapshot(
            EquipmentLocationType locationType,
            UUID departmentId,
            UUID warehouseId,
            EquipmentOutsideReason outsideReason,
            String outsideTakenBy,
            UUID outsideRecipientUserId,
            LocalDate outsideStartedDate,
            LocalDate outsideExpectedReturnDate,
            String outsideDestination,
            String outsideReasonNote
    ) {
    }

    public static EquipmentLocationHistoryResponse from(EquipmentLocationHistory history, Instant activeUntil) {
        return new EquipmentLocationHistoryResponse(
                history.getId(),
                history.getEquipmentId(),
                fromSnapshot(history),
                toSnapshot(history),
                history.getResponsibleDepartmentId(),
                history.getChangedBy(),
                history.getChangedAt(),
                activeUntil,
                durationMinutes(history.getChangedAt(), activeUntil),
                history.getNote()
        );
    }

    private static LocationSnapshot fromSnapshot(EquipmentLocationHistory history) {
        if (history.getFromLocationType() == null
                && history.getFromDepartmentId() == null
                && history.getFromWarehouseId() == null
                && history.getFromOutsideReason() == null) {
            return null;
        }
        return new LocationSnapshot(
                history.getFromLocationType(),
                history.getFromDepartmentId(),
                history.getFromWarehouseId(),
                history.getFromOutsideReason(),
                history.getFromOutsideTakenBy(),
                history.getFromOutsideRecipientUserId(),
                history.getFromOutsideStartedDate(),
                history.getFromOutsideExpectedReturnDate(),
                history.getFromOutsideDestination(),
                history.getFromOutsideReasonNote()
        );
    }

    private static LocationSnapshot toSnapshot(EquipmentLocationHistory history) {
        return new LocationSnapshot(
                history.getToLocationType(),
                history.getToDepartmentId(),
                history.getToWarehouseId(),
                history.getToOutsideReason(),
                history.getToOutsideTakenBy(),
                history.getToOutsideRecipientUserId(),
                history.getToOutsideStartedDate(),
                history.getToOutsideExpectedReturnDate(),
                history.getToOutsideDestination(),
                history.getToOutsideReasonNote()
        );
    }

    private static Long durationMinutes(Instant changedAt, Instant activeUntil) {
        if (changedAt == null) {
            return null;
        }
        Instant end = activeUntil != null ? activeUntil : Instant.now();
        if (end.isBefore(changedAt)) {
            return 0L;
        }
        return Duration.between(changedAt, end).toMinutes();
    }
}
