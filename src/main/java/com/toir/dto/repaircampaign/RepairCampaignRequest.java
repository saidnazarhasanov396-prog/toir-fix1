package com.toir.dto.repaircampaign;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.toir.dto.common.MoneyDecimalStringDeserializer;
import com.toir.dto.sparepartlifecycle.DecimalStringSerializer;
import com.toir.enums.RepairCampaignScopeType;
import com.toir.enums.RepairCampaignPriority;
import com.toir.validation.ValidIsoCurrency;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
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
        @Digits(integer = 15, fraction = 4)
        @JsonSerialize(using = DecimalStringSerializer.class)
        @JsonDeserialize(using = MoneyDecimalStringDeserializer.class)
        BigDecimal totalBudget,
        RepairCampaignScopeType scopeType,
        UUID equipmentTypeId,
        List<@Valid RepairCampaignDepartmentDto> participantDepartments,
        @JsonAlias("description") String description,
        String notes,
        UUID maintenanceBudgetId,
        @NotBlank @ValidIsoCurrency String currencyCode,
        String campaignType,
        UUID responsibleEmployeeId,
        RepairCampaignPriority priority,
        String objective,
        Long version,
        List<@Valid RepairCampaignStageDto> stages
) {
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
            String notes,
            UUID maintenanceBudgetId,
            String currencyCode,
            String campaignType,
            UUID responsibleEmployeeId,
            RepairCampaignPriority priority,
            String objective,
            Long version
    ) {
        this(code, name, departmentId, startDate, endDate, totalBudget,
                scopeType, equipmentTypeId, participantDepartments, description, notes,
                maintenanceBudgetId, currencyCode, campaignType, responsibleEmployeeId, priority, objective,
                version, List.of());
    }

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
                null, null, List.of(), description, notes, null, "UZS",
                null, null, null, null, null, List.of());
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
                scopeType, equipmentTypeId, participantDepartments, description, notes, null, "UZS",
                null, null, null, null, null, List.of());
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
            String notes,
            UUID maintenanceBudgetId,
            String currencyCode
    ) {
        this(code, name, departmentId, startDate, endDate, totalBudget,
                scopeType, equipmentTypeId, participantDepartments, description, notes,
                maintenanceBudgetId, currencyCode, null, null, null, null, null, List.of());
    }

    public RepairCampaignRequest {
        totalBudget = totalBudget == null ? BigDecimal.ZERO : totalBudget;
        currencyCode = currencyCode == null ? "UZS" : currencyCode.trim();
        campaignType = campaignType == null || campaignType.isBlank() ? "REPAIR" : campaignType.trim();
        priority = priority == null ? RepairCampaignPriority.MEDIUM : priority;
        objective = objective == null || objective.isBlank() ? null : objective.trim();
        stages = stages == null ? List.of() : stages;
    }
}
