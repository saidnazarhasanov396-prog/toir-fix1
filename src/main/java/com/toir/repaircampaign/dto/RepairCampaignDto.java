package com.toir.repaircampaign.dto;

import com.toir.repaircampaign.RepairCampaign;
import com.toir.repaircampaign.RepairCampaignStatus;

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
    public static RepairCampaignDto from(RepairCampaign c) {
        return new RepairCampaignDto(
                c.getId(), c.getCode(), c.getName(),
                c.getYear(), c.getQuarter(), c.getDepartmentId(), c.getStatus(),
                c.getStartDate(), c.getEndDate(),
                c.getTotalBudget(), c.getTotalActual(),
                c.getTotalBudget() - c.getTotalActual(),
                c.getScope(), c.getNotes(),
                c.getStages().stream().map(RepairCampaignStageDto::from).toList()
        );
    }
}
