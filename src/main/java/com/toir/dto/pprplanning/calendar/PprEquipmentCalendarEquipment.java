package com.toir.dto.pprplanning.calendar;

import com.toir.enums.EquipmentStatus;
import java.util.UUID;

public record PprEquipmentCalendarEquipment(
        UUID id,
        String code,
        String name,
        String inventoryNumber,
        String technicalNumber,
        EquipmentStatus status,
        boolean deleted,
        String criticalityCode,
        PprEquipmentCalendarNamedRef physicalDepartment,
        PprEquipmentCalendarNamedRef responsibleDepartment,
        PprEquipmentCalendarNamedRef parent,
        PprEquipmentCalendarNamedRef location,
        PprEquipmentCalendarNamedRef equipmentType
) {
}
