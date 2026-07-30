package com.toir.dto.department;

import com.toir.entity.Department;
import com.toir.enums.DepartmentType;

import java.util.List;
import java.util.UUID;

public record DepartmentTreeDto(
        UUID id,
        String name,
        String nameEn,
        String nameUz,
        DepartmentType type,
        UUID parentId,
        String description,
        List<DepartmentTreeDto> children
) {
    public static DepartmentTreeDto from(Department d, List<DepartmentTreeDto> children) {
        return new DepartmentTreeDto(
                d.getId(),
                d.getName(),
                d.getNameEn(),
                d.getNameUz(),
                d.getType(),
                d.getParentId(),
                d.getDescription(),
                children == null ? List.of() : children
        );
    }
}
