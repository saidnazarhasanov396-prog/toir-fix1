package com.toir.repaircampaign.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;
import java.util.UUID;

public record RepairCampaignRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull @Min(2000) Integer year,
        Integer quarter,
        UUID departmentId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @PositiveOrZero double totalBudget,
        String scope,
        String notes
) {}
