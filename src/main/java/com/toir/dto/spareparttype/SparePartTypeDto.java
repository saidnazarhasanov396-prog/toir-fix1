package com.toir.dto.spareparttype;

import com.toir.entity.SparePartType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record SparePartTypeDto(
        UUID id,
        String code,
        String name,
        String description,
        String defaultUnit,
        Boolean active,
        Long sparePartCount,
        BigDecimal sparePartStockCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static SparePartTypeDto from(SparePartType type) {
        return from(type, 0L, BigDecimal.ZERO);
    }

    public static SparePartTypeDto from(SparePartType type, long sparePartCount) {
        return from(type, sparePartCount, BigDecimal.ZERO);
    }

    public static SparePartTypeDto from(SparePartType type, long sparePartCount, BigDecimal sparePartStockCount) {
        return new SparePartTypeDto(
                type.getId(),
                type.getCode(),
                type.getName(),
                type.getDescription(),
                type.getDefaultUnit(),
                type.getActive(),
                sparePartCount,
                sparePartStockCount != null ? sparePartStockCount : BigDecimal.ZERO,
                type.getCreatedAt(),
                type.getUpdatedAt()
        );
    }
}
