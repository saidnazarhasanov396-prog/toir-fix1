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
        String description
) {
    public static LocationDto from(Location l) {
        return new LocationDto(l.getId(), l.getCode(), l.getName(), l.getNameEn(), l.getNameUz(),
                l.getType(), l.getParentId(), l.getDepartmentId(), l.getDescription());
    }
}
