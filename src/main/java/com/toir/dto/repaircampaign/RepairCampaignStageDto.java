package com.toir.dto.repaircampaign;

import com.toir.entity.repair.RepairCampaignStage;
import com.toir.enums.RepairCampaignStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;
import java.util.UUID;

public record RepairCampaignStageDto(
        UUID id,
        @Positive int sequence,
        @NotBlank String name,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @PositiveOrZero double plannedCost,
        @PositiveOrZero double actualCost,
        RepairCampaignStatus status,
        String notes
) {
    public static RepairCampaignStageDto from(RepairCampaignStage s) {
        return new RepairCampaignStageDto(
                s.getId(), s.getSequence(), s.getName(),
                s.getStartDate(), s.getEndDate(),
                s.getPlannedCost(), s.getActualCost(),
                s.getStatus(), s.getNotes()
        );
    }
}
