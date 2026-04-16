package com.toir.conditionreading.dto;

import com.toir.conditionreading.ConditionParameter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record ConditionReadingRequest(
        @NotNull ConditionParameter parameter,
        double value,
        @NotBlank String unit,
        Instant recordedAt,
        Double warnHigh,
        Double alarmHigh,
        Double warnLow,
        Double alarmLow,
        String notes
) {}
