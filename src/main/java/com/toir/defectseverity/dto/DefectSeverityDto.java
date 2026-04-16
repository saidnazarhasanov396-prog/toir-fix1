package com.toir.defectseverity.dto;

import com.toir.defectseverity.DefectSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record DefectSeverityDto(
        UUID id,
        @NotBlank String code,
        @NotBlank String name,
        @PositiveOrZero int weight
) {
    public static DefectSeverityDto from(DefectSeverity s) {
        return new DefectSeverityDto(s.getId(), s.getCode(), s.getName(), s.getWeight());
    }
}
