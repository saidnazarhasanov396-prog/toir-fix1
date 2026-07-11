package com.toir.dto.repaircampaign;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.toir.dto.sparepartlifecycle.DecimalStringSerializer;
import com.toir.entity.repair.RepairCampaign;
import com.toir.enums.RepairCampaignScopeType;
import com.toir.enums.RepairCampaignStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RepairCampaignDto(
        UUID id,
        String code,
        String name,
        UUID departmentId,
        String departmentName,
        RepairCampaignStatus status,
        LocalDate startDate,
        LocalDate endDate,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal totalBudget,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal totalActual,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal variance,
        String description,
        String notes,
        List<RepairCampaignStageDto> stages,
        RepairCampaignScopeType scopeType,
        UUID equipmentTypeId,
        List<RepairCampaignDepartmentDto> participantDepartments,
        int workOrderCount,
        int completedWorkOrderCount,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal approvedActual,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal pendingActual,
        UUID maintenanceBudgetId,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal budgetPlanned,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal budgetActual,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal budgetRemaining,
        String budgetStatus,
        String currencyCode
) {
    public RepairCampaignDto(
            UUID id,
            String code,
            String name,
            UUID departmentId,
            String departmentName,
            RepairCampaignStatus status,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal totalBudget,
            BigDecimal totalActual,
            BigDecimal variance,
            String description,
            String notes,
            List<RepairCampaignStageDto> stages
    ) {
        this(id, code, name, departmentId, departmentName, status, startDate, endDate, totalBudget,
                totalActual, variance, description, notes, stages, RepairCampaignScopeType.CUSTOM, null, List.of(),
                0, 0, totalActual, BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, "UZS");
    }

    public RepairCampaignDto(
            UUID id, String code, String name, UUID departmentId, String departmentName,
            RepairCampaignStatus status, LocalDate startDate, LocalDate endDate,
            BigDecimal totalBudget, BigDecimal totalActual, BigDecimal variance,
            String description, String notes, List<RepairCampaignStageDto> stages, String currencyCode
    ) {
        this(id, code, name, departmentId, departmentName, status, startDate, endDate, totalBudget,
                totalActual, variance, description, notes, stages, RepairCampaignScopeType.CUSTOM, null, List.of(),
                0, 0, totalActual, BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, currencyCode);
    }

    public RepairCampaignDto(
            UUID id,
            String code,
            String name,
            UUID departmentId,
            String departmentName,
            RepairCampaignStatus status,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal totalBudget,
            BigDecimal totalActual,
            BigDecimal variance,
            String description,
            String notes,
            List<RepairCampaignStageDto> stages,
            RepairCampaignScopeType scopeType,
            UUID equipmentTypeId,
            List<RepairCampaignDepartmentDto> participantDepartments,
            int workOrderCount,
            int completedWorkOrderCount,
            BigDecimal approvedActual,
            BigDecimal pendingActual
    ) {
        this(id, code, name, departmentId, departmentName, status, startDate, endDate, totalBudget,
                totalActual, variance, description, notes, stages, scopeType, equipmentTypeId, participantDepartments,
                workOrderCount, completedWorkOrderCount, approvedActual, pendingActual, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, "UZS");
    }

    public static RepairCampaignDto from(RepairCampaign c, String departmentName) {
        return new RepairCampaignDto(
                c.getId(), c.getCode(), c.getName(),
                c.getDepartmentId(), departmentName, c.getStatus(),
                c.getStartDate(), c.getEndDate(),
                c.getTotalBudget(), c.getTotalActual(),
                c.getTotalBudget().subtract(c.getTotalActual()),
                c.getScope(), c.getNotes(),
                c.getStages().stream().map(RepairCampaignStageDto::from).toList(),
                c.getScopeType(),
                c.getEquipmentTypeId(),
                List.of(),
                0,
                0,
                c.getTotalActual(),
                BigDecimal.ZERO,
                c.getMaintenanceBudgetId(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                c.getCurrencyCode()
        );
    }

    public static RepairCampaignDto from(RepairCampaign c) {
        return from(c, null);
    }
}
