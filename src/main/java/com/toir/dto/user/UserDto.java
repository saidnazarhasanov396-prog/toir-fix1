package com.toir.dto.user;

import com.toir.entity.users.Role;
import com.toir.entity.users.User;
import com.toir.enums.UserStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserDto(
        UUID id,
        String username,
        String email,
        String fullName,
        String position,
        String phone,
        UserStatus status,
        DepartmentRef department,
        RoleRef primaryRole,
        List<UserRoleAssignment> userRoles,
        Instant lastLoginAt
) {
    public record DepartmentRef(UUID id, String code, String name) {}
    public record RoleRef(UUID id, String code, String name) {}
    public record UserRoleAssignment(UUID id, RoleRef role, DepartmentRef department) {}

    public static UserDto from(User user) {
        RoleRef primary = null;
        if (user.getPrimaryRole() != null) {
            Role r = user.getPrimaryRole();
            primary = new RoleRef(r.getId(), r.getCode(), r.getName());
        }
        List<UserRoleAssignment> assignments = user.getRoles().stream()
                .map(r -> new UserRoleAssignment(r.getId(), new RoleRef(r.getId(), r.getCode(), r.getName()), null))
                .toList();
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getPosition(),
                user.getPhone(),
                user.getStatus(),
                null,
                primary,
                assignments,
                user.getLastLoginAt()
        );
    }
}
