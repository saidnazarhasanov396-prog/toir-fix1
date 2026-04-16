package com.toir.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateUserRequest(
        @NotBlank String username,
        @Email @NotBlank String email,
        @NotBlank String fullName,
        @NotBlank @Size(min = 8) String password,
        String position,
        String phone,
        UUID departmentId,
        UUID primaryRoleId,
        List<UUID> roleIds
) {}
