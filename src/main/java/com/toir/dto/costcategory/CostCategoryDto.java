package com.toir.dto.costcategory;

import com.toir.entity.projects.CostCategory;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CostCategoryDto(
        UUID id,
        String code,
        @NotBlank String name,
        String description
) {
    public static CostCategoryDto from(CostCategory c) {
        return new CostCategoryDto(c.getId(), c.getCode(), c.getName(), c.getDescription());
    }
}
