package com.toir.dto.profile;

import java.util.UUID;

public record ProfileDepartmentResponse(
        UUID id,
        String name
) {
}
