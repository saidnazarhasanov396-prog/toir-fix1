package com.toir.auth.dto;

import com.toir.common.security.AuthenticatedUser;

public record LoginResponse(
        String accessToken,
        long expiresIn,
        AuthenticatedUser user
) {}
