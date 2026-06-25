package com.toir.dto.repaircampaign;

import com.toir.enums.RepairCampaignScopeType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RepairCampaignRequest(
        String code,
        @NotBlank String name,
        @NotNull @Min(2000) Integer year,
        Integer quarter,
        UUID departmentId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @PositiveOrZero double totalBudget,
        RepairCampaignScopeType scopeType,
        UUID equipmentTypeId,
        List<RepairCampaignDepartmentDto> participantDepartments,
        String scope,
        String notes
) {
    public RepairCampaignRequest(
            String code,
            @NotBlank String name,
            @NotNull @Min(2000) Integer year,
            Integer quarter,
            UUID departmentId,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @PositiveOrZero double totalBudget,
            String scope,
            String notes
    ) {
        this(code, name, year, quarter, departmentId, startDate, endDate, totalBudget,
                null, null, List.of(), scope, notes);
    }
}
