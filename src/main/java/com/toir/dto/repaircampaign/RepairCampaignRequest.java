package com.toir.dto.repaircampaign;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.toir.dto.common.MoneyDecimalStringDeserializer;
import com.toir.dto.sparepartlifecycle.DecimalStringSerializer;
import com.toir.enums.RepairCampaignScopeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RepairCampaignRequest(
        String code,
        @NotBlank String name,
        UUID departmentId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @PositiveOrZero
        @JsonSerialize(using = DecimalStringSerializer.class)
        @JsonDeserialize(using = MoneyDecimalStringDeserializer.class)
        BigDecimal totalBudget,
        RepairCampaignScopeType scopeType,
        UUID equipmentTypeId,
        List<RepairCampaignDepartmentDto> participantDepartments,
        @JsonAlias("description") String description,
        String notes,
        UUID maintenanceBudgetId,
        @NotBlank String currencyCode
) {
    public RepairCampaignRequest(
            String code,
            @NotBlank String name,
            UUID departmentId,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @PositiveOrZero BigDecimal totalBudget,
            String description,
            String notes
    ) {
        this(code, name, departmentId, startDate, endDate, totalBudget,
                null, null, List.of(), description, notes, null, "UZS");
    }

    public RepairCampaignRequest(
            String code,
            @NotBlank String name,
            UUID departmentId,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @PositiveOrZero BigDecimal totalBudget,
            RepairCampaignScopeType scopeType,
            UUID equipmentTypeId,
            List<RepairCampaignDepartmentDto> participantDepartments,
            String description,
            String notes
    ) {
        this(code, name, departmentId, startDate, endDate, totalBudget,
                scopeType, equipmentTypeId, participantDepartments, description, notes, null, "UZS");
    }

    public RepairCampaignRequest {
        totalBudget = totalBudget == null ? BigDecimal.ZERO : totalBudget;
        currencyCode = currencyCode == null ? "UZS" : currencyCode;
    }
}
