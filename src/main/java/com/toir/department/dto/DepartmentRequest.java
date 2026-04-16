package com.toir.department.dto;

import com.toir.department.DepartmentType;
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
