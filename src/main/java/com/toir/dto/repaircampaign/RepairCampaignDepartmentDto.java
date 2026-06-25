package com.toir.dto.repaircampaign;

import com.toir.entity.repair.RepairCampaignDepartment;
import com.toir.enums.RepairCampaignDepartmentRole;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record RepairCampaignDepartmentDto(
        UUID id,
        @NotNull UUID departmentId,
        String departmentName,
        @NotNull RepairCampaignDepartmentRole role,
        @PositiveOrZero double plannedBudget,
        String notes
) {
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
