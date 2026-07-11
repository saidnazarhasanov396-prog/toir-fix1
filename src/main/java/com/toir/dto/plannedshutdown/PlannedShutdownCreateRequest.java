package com.toir.dto.plannedshutdown;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PlannedShutdownCreateRequest(
        @Pattern(regexp = "[A-Za-z0-9-]{3,64}") String code,
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 64) String shutdownType,
        @NotNull UUID departmentId,
        @NotNull UUID responsibleEmployeeId,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        @NotBlank String reason,
        String objective,
        String notes,
        @Size(max = 32) String riskLevel,
        @DecimalMin("0") @Digits(integer = 5, fraction = 4) BigDecimal riskScore
) {}
