package com.toir.dto.defectlist;

import com.toir.entity.DefectListLine;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record DefectListLineDto(
        UUID id,
        UUID defectId,
        @NotBlank String description,
        String workScope,
        String materialSpecification,
        UUID sparePartId,
        @PositiveOrZero double requiredQuantity,
        @PositiveOrZero double estimatedLaborHours,
        @PositiveOrZero double estimatedCost
) {
    public static DefectListLineDto from(DefectListLine l) {
        return new DefectListLineDto(
                l.getId(),
                l.getDefectId(),
                l.getDescription(),
                l.getWorkScope(),
                l.getMaterialSpecification(),
                l.getSparePartId(),
                l.getRequiredQuantity(),
                l.getEstimatedLaborHours(),
                l.getEstimatedCost()
        );
    }
}
