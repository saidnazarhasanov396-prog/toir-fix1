package com.toir.location.dto;

import com.toir.location.LocationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record LocationRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull LocationType type,
        UUID parentId,
        UUID departmentId,
        String description
) {}
