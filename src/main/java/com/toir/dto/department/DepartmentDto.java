package com.toir.dto.department;

import com.toir.entity.Department;
import com.toir.entity.DepartmentType;

import java.util.UUID;

public record DepartmentDto(
        UUID id,
        String code,
        String name,
        String nameEn,
        String nameUz,
        DepartmentType type,
        UUID parentId,
        String description
) {
    public static DepartmentDto from(Department d) {
        return new DepartmentDto(d.getId(), d.getCode(), d.getName(), d.getNameEn(), d.getNameUz(),
                d.getType(), d.getParentId(), d.getDescription());
    }
}
