package com.toir.dto.user;

import com.toir.enums.UserStatus;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;
import java.util.UUID;

public record UserFilterRequest(
        String search,
        String username,
        String email,
        String fullName,
        String position,
        String phone,
        UserStatus status,
        UUID departmentId,
        UUID primaryRoleId,
        String primaryRoleCode,
        UUID roleId,
        String roleCode,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant lastLoginFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant lastLoginTo
) {
}
