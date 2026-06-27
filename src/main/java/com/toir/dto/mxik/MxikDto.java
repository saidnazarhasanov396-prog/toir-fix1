package com.toir.dto.mxik;

import com.toir.entity.Mxik;

import java.util.UUID;

public record MxikDto(
        UUID id,
        String name,
        String nameUzLatn,
        String nameRu,
        String kod,
        String type,
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
    public static MxikDto from(Mxik mxik) {
        return new MxikDto(
                mxik.getId(),
                mxik.getName(),
                mxik.getNameUzLatn(),
                mxik.getNameRu(),
                mxik.getKod(),
                mxik.getType(),
                mxik.getGroupName(),
                mxik.getGroupNameRu(),
                mxik.getGroupNameCyril(),
                mxik.getClassName(),
                mxik.getClassNameRu(),
                mxik.getClassNameCyril(),
                mxik.getPositionName(),
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
