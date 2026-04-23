package com.toir.dto.department;

import com.toir.entity.DepartmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DepartmentRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull DepartmentType type,
        UUID parentId,
        String description
) {}
