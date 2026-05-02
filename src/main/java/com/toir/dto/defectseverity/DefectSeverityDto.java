package com.toir.dto.defectseverity;

import com.toir.entity.DefectSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record DefectSeverityDto(
        UUID id,
        String code,
        @NotBlank String name,
        @PositiveOrZero int weight
) {
    public static DefectSeverityDto from(DefectSeverity s) {
        return new DefectSeverityDto(s.getId(), s.getCode(), s.getName(), s.getWeight());
    }
}
