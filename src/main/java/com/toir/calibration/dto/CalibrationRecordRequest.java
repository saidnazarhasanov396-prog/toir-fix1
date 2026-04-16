package com.toir.calibration.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CalibrationRecordRequest(
        @NotNull UUID equipmentId,
        String certificateNumber,
        String performedBy,
        @NotNull LocalDate performedAt,
        LocalDate nextDueAt,
        String result,
        Double tolerance,
        Double measuredError,
        String unit,
        UUID documentFileId,
        String notes
) {}
