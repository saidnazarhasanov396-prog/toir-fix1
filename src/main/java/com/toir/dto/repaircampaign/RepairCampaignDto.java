package com.toir.dto.repaircampaign;

import com.toir.entity.repair.RepairCampaign;
import com.toir.enums.RepairCampaignScopeType;
import com.toir.enums.RepairCampaignStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RepairCampaignDto(
        UUID id,
        String code,
        String name,
        int year,
        Integer quarter,
        UUID departmentId,
        String departmentName,
        RepairCampaignStatus status,
        LocalDate startDate,
        LocalDate endDate,
        double totalBudget,
        double totalActual,
        double variance,
        String scope,
        String notes,
        List<RepairCampaignStageDto> stages,
        RepairCampaignScopeType scopeType,
        UUID equipmentTypeId,
        List<RepairCampaignDepartmentDto> participantDepartments,
        int workOrderCount,
        int completedWorkOrderCount,
        double approvedActual,
        double pendingActual
) {
    public RepairCampaignDto(
            UUID id,
            String code,
            String name,
            int year,
            Integer quarter,
            UUID departmentId,
            String departmentName,
            RepairCampaignStatus status,
            LocalDate startDate,
            LocalDate endDate,
            double totalBudget,
            double totalActual,
            double variance,
            String scope,
            String notes,
            List<RepairCampaignStageDto> stages
    ) {
        this(id, code, name, year, quarter, departmentId, departmentName, status, startDate, endDate, totalBudget,
                totalActual, variance, scope, notes, stages, RepairCampaignScopeType.CUSTOM, null, List.of(),
                0, 0, totalActual, 0);
    }

    public static RepairCampaignDto from(RepairCampaign c, String departmentName) {
        return new RepairCampaignDto(
                c.getId(), c.getCode(), c.getName(),
                c.getYear(), c.getQuarter(), c.getDepartmentId(), departmentName, c.getStatus(),
                c.getStartDate(), c.getEndDate(),
                c.getTotalBudget(), c.getTotalActual(),
                c.getTotalBudget() - c.getTotalActual(),
                c.getScope(), c.getNotes(),
                c.getStages().stream().map(RepairCampaignStageDto::from).toList(),
                c.getScopeType(),
                c.getEquipmentTypeId(),
                List.of(),
                0,
                0,
                c.getTotalActual(),
                0
        );
    }

    public static RepairCampaignDto from(RepairCampaign c) {
        return from(c, null);
    }
}
