package com.toir.dto.mxik;

import com.toir.entity.Mxik;

import java.util.UUID;

public record MxikDto(
        UUID id,
        String name,
        String kod,
        String type,
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
    public static MxikDto from(Mxik mxik) {
        return new MxikDto(
                mxik.getId(),
                mxik.getName(),
                mxik.getKod(),
                mxik.getType(),
                mxik.getGroupName(),
                mxik.getPositionName(),
                mxik.getNameUzLatn(),
                mxik.getNameRu(),
                mxik.getGroupNameRu(),
                mxik.getGroupNameCyril(),
                mxik.getClassName(),
                mxik.getClassNameRu(),
                mxik.getClassNameCyril(),
                mxik.getPositionNameRu(),
                mxik.getPositionNameCyril(),
                mxik.getSubPositionName(),
                mxik.getSubPositionNameRu(),
                mxik.getSubPositionNameCyril(),
                mxik.getBrandName(),
                mxik.getBrandNameRu(),
                mxik.getBrandNameCyril(),
                mxik.getAttributeName(),
                mxik.getAttributeNameRu(),
                mxik.getAttributeNameCyril(),
                mxik.getBarcode()
        );
    }
}
