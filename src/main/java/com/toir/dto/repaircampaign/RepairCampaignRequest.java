package com.toir.dto.repaircampaign;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.toir.enums.RepairCampaignScopeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RepairCampaignRequest(
        String code,
        @NotBlank String name,
        UUID departmentId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @PositiveOrZero double totalBudget,
        RepairCampaignScopeType scopeType,
        UUID equipmentTypeId,
        List<RepairCampaignDepartmentDto> participantDepartments,
        @JsonAlias("description") String description,
        String notes,
        UUID maintenanceBudgetId
) {
    public RepairCampaignRequest(
            String code,
            @NotBlank String name,
            UUID departmentId,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @PositiveOrZero double totalBudget,
            String description,
            String notes
    ) {
        this(code, name, departmentId, startDate, endDate, totalBudget,
                null, null, List.of(), description, notes, null);
    }

    public RepairCampaignRequest(
            String code,
            @NotBlank String name,
            UUID departmentId,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @PositiveOrZero double totalBudget,
            RepairCampaignScopeType scopeType,
            UUID equipmentTypeId,
            List<RepairCampaignDepartmentDto> participantDepartments,
            String description,
            String notes
    ) {
        this(code, name, departmentId, startDate, endDate, totalBudget,
                scopeType, equipmentTypeId, participantDepartments, description, notes, null);
    }
}
