package com.toir.dto.profile;

import java.util.List;
import java.util.UUID;

public record ProfileResponse(
        UUID id,
        String username,
        String email,
        String firstName,
        String lastName,
        String displayName,
        String avatarUrl,
        String avatarVersion,
        ProfileDepartmentResponse department,
        List<String> roles
) {
}
