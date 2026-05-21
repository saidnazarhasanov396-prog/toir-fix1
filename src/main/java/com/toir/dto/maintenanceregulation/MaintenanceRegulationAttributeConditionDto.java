package com.toir.dto.maintenanceregulation;

import com.toir.entity.maintenance.MaintenanceRegulationAttributeCondition;
import com.toir.enums.MaintenanceRegulationConditionOperator;

import java.time.LocalDate;
import java.util.UUID;

public record MaintenanceRegulationAttributeConditionDto(
        UUID id,
        UUID regulationId,
        String attributeKey,
        MaintenanceRegulationConditionOperator operator,
        String valueText,
        Double valueNumber,
        LocalDate valueDate,
        Boolean valueBoolean,
        String valueOption
) {
    public static MaintenanceRegulationAttributeConditionDto from(MaintenanceRegulationAttributeCondition condition) {
        return new MaintenanceRegulationAttributeConditionDto(
                condition.getId(),
                condition.getRegulationId(),
                condition.getAttributeKey(),
                condition.getOperator(),
                condition.getValueText(),
                condition.getValueNumber(),
                condition.getValueDate(),
                condition.getValueBoolean(),
                condition.getValueOption()
        );
    }
}
