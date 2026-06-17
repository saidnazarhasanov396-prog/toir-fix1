package com.toir.dto.equipmentpassport;

import com.toir.enums.ProductivityTimeUnit;
import jakarta.validation.constraints.NotNull;

public record ProductivityEntryDto(
        String productName,
        @NotNull Double capacity,
        @NotNull ProductivityTimeUnit timeUnit
) {}
