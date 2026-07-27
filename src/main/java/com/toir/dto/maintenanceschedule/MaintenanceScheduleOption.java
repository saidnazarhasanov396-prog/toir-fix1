package com.toir.dto.maintenanceschedule;

import com.toir.enums.MaintenanceScheduleScopeType;
import java.util.UUID;

public record MaintenanceScheduleOption(
        UUID id,
        String code,
        String name,
        MaintenanceScheduleScopeType scopeType,
        long eligibleEquipmentCount
) {
}
