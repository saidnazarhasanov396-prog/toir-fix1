package com.toir.certification.dto;

import com.toir.certification.CertificationType;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CertificationTypeDto(
        UUID id,
        @NotBlank String code,
        @NotBlank String name,
        String nameEn,
        String nameUz,
        Integer validityMonths,
        String category,
        String description
) {
    public static CertificationTypeDto from(CertificationType c) {
        return new CertificationTypeDto(
                c.getId(), c.getCode(), c.getName(), c.getNameEn(), c.getNameUz(),
                c.getValidityMonths(), c.getCategory(), c.getDescription()
        );
    }
}
