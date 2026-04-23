package com.toir.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
        @NotBlank String username,
        @NotBlank String roleCode,
        @NotBlank @Email String email,
        @NotBlank String fullName,
        @NotBlank String password
) {}
