package com.toir.location.dto;

import com.toir.location.Location;
import com.toir.location.LocationType;

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
