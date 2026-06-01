package com.toir.dto.maintenanceregulation;

import java.util.List;
import java.util.UUID;

public record EquipmentWithRegulationsDto(
        UUID equipmentId,
        String equipmentName,
        String equipmentCode,
        UUID equipmentTypeId,
        String equipmentTypeName,
        List<MaintenanceRegulationSummaryDto> regulations
) {}
