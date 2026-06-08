package com.toir.dto.audit;

import com.toir.dto.user.UserDto;
import com.toir.enums.UserStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuditLogUserSummary(
        UUID id,
        String username,
        String email,
        String fullName,
        String position,
        String phone,
        UserStatus status,
        Instant lastLoginAt,
        UUID departmentId,
        String departmentCode,
        String departmentName,
        UUID primaryRoleId,
        String primaryRoleCode,
        String primaryRoleName
) {
    public UserDto toUserDto() {
        UserDto.DepartmentRef department = departmentName == null
                ? null
                : new UserDto.DepartmentRef(departmentId, departmentCode, departmentName);
        UserDto.RoleRef primaryRole = primaryRoleId == null
                ? null
                : new UserDto.RoleRef(primaryRoleId, primaryRoleCode, primaryRoleName);
        return new UserDto(
                id,
                username,
                email,
                fullName,
                position,
                phone,
                status,
                department,
                primaryRole,
                List.of(),
                lastLoginAt
        );
    }
}
