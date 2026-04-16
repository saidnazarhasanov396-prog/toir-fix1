package com.toir.role.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record RoleRequest(
        @NotBlank String code,
        @NotBlank String name,
        String description,
        List<String> permissions
) {}
