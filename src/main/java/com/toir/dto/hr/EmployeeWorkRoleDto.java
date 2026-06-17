package com.toir.dto.hr;

import com.toir.entity.users.EmployeeWorkRole;

import java.util.UUID;

public record EmployeeWorkRoleDto(
        UUID id,
        String code,
        String name,
        String nameEn,
        String nameUz,
        String description,
        boolean active
) {
    public static EmployeeWorkRoleDto from(EmployeeWorkRole role) {
        return new EmployeeWorkRoleDto(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getNameEn(),
                role.getNameUz(),
                role.getDescription(),
                role.isActive()
        );
    }
}
