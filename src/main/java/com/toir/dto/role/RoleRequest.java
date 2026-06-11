package com.toir.dto.role;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record RoleRequest(
        String code,
        @NotBlank String name,
        String description,
        List<String> permissions
) {}
