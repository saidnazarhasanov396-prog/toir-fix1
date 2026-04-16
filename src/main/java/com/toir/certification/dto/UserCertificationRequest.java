package com.toir.certification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record UserCertificationRequest(
        @NotNull UUID userId,
        @NotBlank String typeCode,
        String certificateNumber,
        String issuedBy,
        @NotNull LocalDate issuedAt,
        LocalDate expiresAt,
        String gradeOrLevel,
        UUID documentFileId,
        String notes
) {}
