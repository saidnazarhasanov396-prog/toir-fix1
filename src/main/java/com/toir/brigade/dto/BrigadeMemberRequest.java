package com.toir.brigade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record BrigadeMemberRequest(
        @NotNull UUID userId,
        @NotBlank String roleCode,
        Integer grade,
        List<String> qualifications,
        Boolean active
) {}
