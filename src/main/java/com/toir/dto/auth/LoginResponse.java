package com.toir.dto.auth;

import com.toir.security.AuthenticatedUser;

public record LoginResponse(
        String accessToken,
        long expiresIn,
        AuthenticatedUser user
) {}
