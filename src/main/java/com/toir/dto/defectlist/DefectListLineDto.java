package com.toir.dto.defectlist;

import com.toir.entity.defects.DefectListLine;
import com.toir.enums.DefectOrigin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record DefectListLineDto(
        UUID id,
        UUID defectId,
        DefectOrigin defectOrigin,
        @NotBlank String description,
        String workScope,
        String materialSpecification,
        UUID sparePartId,
        @PositiveOrZero double requiredQuantity,
        @PositiveOrZero double estimatedLaborHours,
        @PositiveOrZero double estimatedCost
) {
    public DefectListLineDto(
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
        this(id, defectId, DefectOrigin.UNKNOWN, description, workScope, materialSpecification, sparePartId,
                requiredQuantity, estimatedLaborHours, estimatedCost);
    }

    public static DefectListLineDto from(DefectListLine l) {
        return new DefectListLineDto(
                l.getId(),
                l.getDefectId(),
                l.getDefectOrigin(),
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
