package com.toir.dto.mxik;

import com.toir.entity.Mxik;

import java.util.UUID;

public record MxikRefDto(
        UUID id,
        String kod,
        String name,
        String type,
        String groupName,
        String className,
        String positionName,
        String subPositionName,
        String brandName,
        String barcode
) {
    public static MxikRefDto from(Mxik mxik) {
        if (mxik == null) {
            return null;
        }
        return new MxikRefDto(
                mxik.getId(),
                mxik.getKod(),
                mxik.getName(),
                mxik.getType(),
                mxik.getGroupName(),
                mxik.getClassName(),
                mxik.getPositionName(),
                mxik.getSubPositionName(),
                mxik.getBrandName(),
                mxik.getBarcode()
        );
    }
}
