package com.toir.dto.equipment;

import com.toir.enums.EquipmentOutsideReason;
import com.toir.enums.PlacementType;
import com.toir.enums.WarehouseEquipmentStatus;

import java.time.LocalDate;
import java.util.UUID;

public record EquipmentLocationDto(
        PlacementType type,
        EquipmentDto.Ref department,
        EquipmentDto.Ref warehouse,
        WarehouseEquipmentStatus warehouseStatus,
        EquipmentDto.Ref location,
        UUID responsibleDepartmentId,
        EquipmentOutsideReason outsideReason,
        String outsideTakenBy,
        UUID outsideRecipientUserId,
        LocalDate outsideStartedDate,
        LocalDate outsideExpectedReturnDate,
        String outsideDestination,
        String outsideReasonNote,
        boolean overdue
) {
}
