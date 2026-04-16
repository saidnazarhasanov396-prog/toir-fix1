package com.toir.brigade.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record BrigadeRequest(
        @NotBlank String code,
        @NotBlank String name,
        UUID departmentId,
        UUID foremanId,
        String specialization,
        Boolean active
) {}
