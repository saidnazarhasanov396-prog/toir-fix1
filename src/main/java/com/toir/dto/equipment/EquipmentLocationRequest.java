package com.toir.dto.equipment;

import com.toir.enums.EquipmentLocationType;
import com.toir.enums.EquipmentOutsideReason;
import com.toir.enums.WarehouseEquipmentStatus;

import java.time.LocalDate;
import java.util.UUID;

public record EquipmentLocationRequest(
        EquipmentLocationType locationType,
        UUID departmentId,
        UUID warehouseId,
        UUID locationId,
        UUID responsibleDepartmentId,
        EquipmentOutsideReason outsideReason,
        String outsideTakenBy,
        UUID outsideRecipientUserId,
        LocalDate outsideStartedDate,
        LocalDate outsideExpectedReturnDate,
        String outsideDestination,
        String outsideReasonNote,
        WarehouseEquipmentStatus warehouseStatus
) {
}
