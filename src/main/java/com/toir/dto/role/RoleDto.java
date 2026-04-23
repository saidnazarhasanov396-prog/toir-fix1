package com.toir.dto.role;

import com.toir.entity.Role;

import java.util.List;
import java.util.UUID;

public record RoleDto(
        UUID id,
        String code,
        String name,
        String nameEn,
        String nameUz,
        String description,
        boolean system,
        List<String> permissions
) {
    public static RoleDto from(Role role) {
        return new RoleDto(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getNameEn(),
                role.getNameUz(),
                role.getDescription(),
                role.isSystem(),
                role.getPermissions()
        );
    }
}
