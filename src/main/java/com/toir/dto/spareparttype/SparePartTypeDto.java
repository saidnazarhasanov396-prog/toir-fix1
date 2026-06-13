package com.toir.dto.spareparttype;

import com.toir.entity.SparePartType;

import java.time.LocalDateTime;
import java.util.UUID;

public record SparePartTypeDto(
        UUID id,
        String code,
        String name,
        String description,
        String defaultUnit,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static SparePartTypeDto from(SparePartType type) {
        return new SparePartTypeDto(
                type.getId(),
                type.getCode(),
                type.getName(),
                type.getDescription(),
                type.getDefaultUnit(),
                type.getActive(),
                type.getCreatedAt(),
                type.getUpdatedAt()
        );
    }
}
