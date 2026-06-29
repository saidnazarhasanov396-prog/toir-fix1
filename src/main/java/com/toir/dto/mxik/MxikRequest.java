package com.toir.dto.mxik;

import jakarta.validation.constraints.NotBlank;

public record MxikRequest(
        @NotBlank String name,
        @NotBlank String kod,
        @NotBlank String type,
        String groupName,
        String positionName,
        String nameUzLatn,
        String nameRu,
        String groupNameRu,
        String groupNameCyril,
        String className,
        String classNameRu,
        String classNameCyril,
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
