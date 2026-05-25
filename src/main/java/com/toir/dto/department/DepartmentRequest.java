package com.toir.dto.department;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.toir.enums.DepartmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@JsonIgnoreProperties("code")
public record DepartmentRequest(
        @NotBlank String name,
        @NotNull DepartmentType type,
        UUID parentId,
        String description
) {}
