package com.toir.dto.mxik;

import com.toir.entity.Mxik;

import java.util.UUID;

public record MxikDto(
        UUID id,
        String name,
        String kod,
        String type,
        String groupName,
        String positionName
) {
    public static MxikDto from(Mxik mxik) {
        return new MxikDto(
                mxik.getId(),
                mxik.getName(),
                mxik.getKod(),
                mxik.getType(),
                mxik.getGroupName(),
                mxik.getPositionName()
        );
    }
}
