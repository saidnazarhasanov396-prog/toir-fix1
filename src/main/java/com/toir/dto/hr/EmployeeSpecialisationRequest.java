package com.toir.dto.hr;

import jakarta.validation.constraints.NotBlank;

public record EmployeeSpecialisationRequest(
        @NotBlank String nameRu,
        @NotBlank String nameEn,
        @NotBlank String nameUz,
        Boolean active
) {
}
