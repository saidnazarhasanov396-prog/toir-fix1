package com.toir.costcategory.dto;

import com.toir.costcategory.CostCategory;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CostCategoryDto(
        UUID id,
        @NotBlank String code,
        @NotBlank String name,
        String description
) {
    public static CostCategoryDto from(CostCategory c) {
        return new CostCategoryDto(c.getId(), c.getCode(), c.getName(), c.getDescription());
    }
}
