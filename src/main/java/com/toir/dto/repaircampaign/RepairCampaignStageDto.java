package com.toir.dto.repaircampaign;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.toir.dto.common.MoneyDecimalStringDeserializer;
import com.toir.dto.sparepartlifecycle.DecimalStringSerializer;
import com.toir.entity.repair.RepairCampaignStage;
import com.toir.enums.RepairCampaignStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RepairCampaignStageDto(
        UUID id,
        @Positive int sequence,
        @NotBlank String name,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @PositiveOrZero @Digits(integer = 15, fraction = 4) @JsonSerialize(using = DecimalStringSerializer.class)
        @JsonDeserialize(using = MoneyDecimalStringDeserializer.class) BigDecimal plannedCost,
        @PositiveOrZero @Digits(integer = 15, fraction = 4) @JsonSerialize(using = DecimalStringSerializer.class)
        @JsonDeserialize(using = MoneyDecimalStringDeserializer.class) BigDecimal actualCost,
        RepairCampaignStatus status,
        String notes,
        int workOrderCount,
        int completedWorkOrderCount,
        @Digits(integer = 15, fraction = 4) @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal approvedActual,
        @Digits(integer = 15, fraction = 4) @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal pendingActual,
        UUID budgetLineId,
        @Digits(integer = 15, fraction = 4) @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal budgetLinePlanned,
        @Digits(integer = 15, fraction = 4) @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal budgetLineActual,
        @Digits(integer = 15, fraction = 4) @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal budgetLineRemaining
) {
    public RepairCampaignStageDto {
        plannedCost = plannedCost == null ? BigDecimal.ZERO : plannedCost;
        actualCost = actualCost == null ? BigDecimal.ZERO : actualCost;
        approvedActual = approvedActual == null ? BigDecimal.ZERO : approvedActual;
        pendingActual = pendingActual == null ? BigDecimal.ZERO : pendingActual;
        budgetLinePlanned = budgetLinePlanned == null ? BigDecimal.ZERO : budgetLinePlanned;
        budgetLineActual = budgetLineActual == null ? BigDecimal.ZERO : budgetLineActual;
        budgetLineRemaining = budgetLineRemaining == null ? BigDecimal.ZERO : budgetLineRemaining;
    }

    public RepairCampaignStageDto(
            UUID id,
            @Positive int sequence,
            @NotBlank String name,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @PositiveOrZero BigDecimal plannedCost,
            @PositiveOrZero BigDecimal actualCost,
            RepairCampaignStatus status,
            String notes
    ) {
        this(id, sequence, name, startDate, endDate, plannedCost, actualCost, status, notes, 0, 0, actualCost,
                BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public RepairCampaignStageDto(
            UUID id,
            @Positive int sequence,
            @NotBlank String name,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @PositiveOrZero BigDecimal plannedCost,
            @PositiveOrZero BigDecimal actualCost,
            RepairCampaignStatus status,
            String notes,
            int workOrderCount,
            int completedWorkOrderCount,
            BigDecimal approvedActual,
            BigDecimal pendingActual
    ) {
        this(id, sequence, name, startDate, endDate, plannedCost, actualCost, status, notes, workOrderCount,
                completedWorkOrderCount, approvedActual, pendingActual, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public static RepairCampaignStageDto from(RepairCampaignStage s) {
        return new RepairCampaignStageDto(
                s.getId(), s.getSequence(), s.getName(),
                s.getStartDate(), s.getEndDate(),
                s.getPlannedCost(), s.getActualCost(),
                s.getStatus(), s.getNotes(),
                0, 0, s.getActualCost(), BigDecimal.ZERO,
                s.getBudgetLineId(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );
    }
}
