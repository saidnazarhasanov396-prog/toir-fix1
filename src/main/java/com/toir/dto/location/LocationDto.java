package com.toir.dto.location;

import com.toir.entity.Location;
import com.toir.enums.LocationType;

import java.util.UUID;

public record LocationDto(
        UUID id,
        String code,
        String name,
        String nameEn,
        String nameUz,
        LocationType type,
        UUID parentId,
        UUID departmentId,
        String departmentName,
        String description
) {
    public LocationDto(
            UUID id,
            String code,
            String name,
            String nameEn,
            String nameUz,
            LocationType type,
            UUID parentId,
            UUID departmentId,
            String description
    ) {
        this(id, code, name, nameEn, nameUz, type, parentId, departmentId, null, description);
    }

    public static LocationDto from(Location l) {
        return from(l, null);
    }

    public static LocationDto from(Location l, String departmentName) {
        return new LocationDto(l.getId(), l.getCode(), l.getName(), l.getNameEn(), l.getNameUz(),
                l.getType(), l.getParentId(), l.getDepartmentId(), departmentName, l.getDescription());
    }
}
