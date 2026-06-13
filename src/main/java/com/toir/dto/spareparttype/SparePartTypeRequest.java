package com.toir.dto.spareparttype;

import jakarta.validation.constraints.NotBlank;

public record SparePartTypeRequest(
        String code,
        @NotBlank String name,
        String description,
        String defaultUnit,
        Boolean active
) {
}
