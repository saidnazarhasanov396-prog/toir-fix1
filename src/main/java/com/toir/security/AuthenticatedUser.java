package com.toir.security;

import java.util.List;

public record AuthenticatedUser(
        String id,
        String username,
        String email,
        String fullName,
        String departmentId,
        String primaryRoleCode,
        List<String> permissions
) {}
