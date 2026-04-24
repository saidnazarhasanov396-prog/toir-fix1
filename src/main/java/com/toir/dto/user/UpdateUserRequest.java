package com.toir.dto.user;

import com.toir.enums.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public record UpdateUserRequest(
        @Email @NotBlank String email,
        @NotBlank String fullName,
        String position,
        String phone,
        UserStatus status,
        UUID departmentId,
        UUID primaryRoleId,
        List<UUID> roleIds
) {}
