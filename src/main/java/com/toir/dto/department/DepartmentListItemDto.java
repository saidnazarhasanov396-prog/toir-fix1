package com.toir.dto.department;

import com.toir.entity.Department;
import com.toir.enums.DepartmentType;

import java.util.UUID;

public record DepartmentListItemDto(
        UUID id,
        String name,
        String nameEn,
        String nameUz,
        DepartmentType type,
        UUID parentId,
        String description
) {
    public static DepartmentListItemDto from(Department department) {
        return new DepartmentListItemDto(
                department.getId(),
                department.getName(),
                department.getNameEn(),
                department.getNameUz(),
                department.getType(),
                department.getParentId(),
                department.getDescription()
        );
    }
}
