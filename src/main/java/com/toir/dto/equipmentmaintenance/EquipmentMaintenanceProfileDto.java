package com.toir.dto.equipmentmaintenance;

import com.toir.dto.maintenanceregulation.MaintenanceRegulationDto;
import java.util.List;
import java.util.UUID;

public record EquipmentMaintenanceProfileDto(
        UUID equipmentId,
        UUID equipmentTypeId,
        List<MaintenanceRegulationDto> inheritedRules,
        List<EquipmentMaintenanceRuleDto> individualRules
) {}
