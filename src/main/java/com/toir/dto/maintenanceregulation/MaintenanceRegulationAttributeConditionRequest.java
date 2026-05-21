package com.toir.dto.maintenanceregulation;

import com.toir.enums.MaintenanceRegulationConditionOperator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record MaintenanceRegulationAttributeConditionRequest(
        @NotBlank String attributeKey,
        @NotNull MaintenanceRegulationConditionOperator operator,
        String valueText,
        Double valueNumber,
        LocalDate valueDate,
        Boolean valueBoolean,
        String valueOption
) {}
