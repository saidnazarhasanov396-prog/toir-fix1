package com.toir.dto.calibration;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CalibrationRecordRequest(
        @NotNull UUID equipmentId,
        @NotBlank(message = "certificateNumber is required")
        @Size(min = 3, max = 64, message = "certificateNumber length must be between 3 and 64")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z0-9/_-]+$",
                message = "certificateNumber must contain at least one letter and one digit and only letters, digits, '-', '/', '_'"
        )
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
