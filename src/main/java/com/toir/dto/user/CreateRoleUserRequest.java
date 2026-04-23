package com.toir.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateRoleUserRequest(
        @NotBlank String roleCode,
        @NotBlank @Size(min = 8) String password,
        String email,
        String fullName,
        UUID departmentId,
        String position,
        String phone
) {}

