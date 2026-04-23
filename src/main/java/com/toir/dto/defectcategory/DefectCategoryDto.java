package com.toir.dto.defectcategory;

import com.toir.entity.DefectCategory;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record DefectCategoryDto(
        UUID id,
        @NotBlank String code,
        @NotBlank String name,
        String description
) {
    public static DefectCategoryDto from(DefectCategory c) {
        return new DefectCategoryDto(c.getId(), c.getCode(), c.getName(), c.getDescription());
    }
}
