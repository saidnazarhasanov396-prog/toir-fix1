package com.toir.dto.repaircampaign;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.toir.dto.common.MoneyDecimalStringDeserializer;
import com.toir.dto.sparepartlifecycle.DecimalStringSerializer;
import com.toir.entity.repair.RepairCampaignDepartment;
import com.toir.enums.RepairCampaignDepartmentRole;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

public record RepairCampaignDepartmentDto(
        UUID id,
        @NotNull UUID departmentId,
        String departmentName,
        @NotNull RepairCampaignDepartmentRole role,
        @PositiveOrZero
        @JsonSerialize(using = DecimalStringSerializer.class)
        @JsonDeserialize(using = MoneyDecimalStringDeserializer.class)
        BigDecimal plannedBudget,
        String notes
) {
    public RepairCampaignDepartmentDto {
        plannedBudget = plannedBudget == null ? BigDecimal.ZERO : plannedBudget;
    }

    public static RepairCampaignDepartmentDto from(RepairCampaignDepartment department, String departmentName) {
        return new RepairCampaignDepartmentDto(
                department.getId(),
                department.getDepartmentId(),
                departmentName,
                department.getRole(),
                department.getPlannedBudget(),
                department.getNotes()
        );
    }
}
