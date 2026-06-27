package com.toir.dto.mxik;

import jakarta.validation.constraints.NotBlank;

public record MxikRequest(
        @NotBlank String name,
        String nameUzLatn,
        String nameRu,
        @NotBlank String kod,
        @NotBlank String type,
        String groupName,
        String groupNameRu,
        String groupNameCyril,
        String className,
        String classNameRu,
        String classNameCyril,
        String positionName,
        String positionNameRu,
        String positionNameCyril,
        String subPositionName,
        String subPositionNameRu,
        String subPositionNameCyril,
        String brandName,
        String brandNameRu,
        String brandNameCyril,
        String attributeName,
        String attributeNameRu,
        String attributeNameCyril,
        String barcode
) {
}
