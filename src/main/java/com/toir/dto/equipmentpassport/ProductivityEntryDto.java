package com.toir.dto.equipmentpassport;

import com.toir.enums.ProductivityTimeUnit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ProductivityEntryDto(
        @NotBlank String productName,
        @NotNull @Positive Double capacity,
        @NotNull ProductivityTimeUnit timeUnit
) {}
