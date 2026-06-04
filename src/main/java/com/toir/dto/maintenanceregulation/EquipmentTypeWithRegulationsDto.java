package com.toir.dto.maintenanceregulation;

import java.util.List;
import java.util.UUID;

public record EquipmentTypeWithRegulationsDto(
        UUID equipmentTypeId,
        String equipmentTypeCode,
        String equipmentTypeName,
        String equipmentTypeCategory,
        Integer equipmentCount,
        List<MaintenanceRegulationSummaryDto> regulations
) {}
