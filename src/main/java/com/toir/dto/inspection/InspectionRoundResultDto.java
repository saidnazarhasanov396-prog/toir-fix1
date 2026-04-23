package com.toir.dto.inspection;

import com.toir.entity.InspectionRoundResult;

import java.util.List;
import java.util.UUID;

public record InspectionRoundResultDto(
        UUID id,
        UUID roundId,
        UUID checkpointId,
        String status,
        Double measuredValue,
        String measuredUnit,
        String comment,
        UUID defectId,
        List<UUID> photoFileIds
) {
    public static InspectionRoundResultDto from(InspectionRoundResult r) {
        return new InspectionRoundResultDto(
                r.getId(),
                r.getRound() != null ? r.getRound().getId() : null,
                r.getCheckpointId(),
                r.getStatus(),
                r.getMeasuredValue(),
                r.getMeasuredUnit(),
                r.getComment(),
                r.getDefectId(),
                r.getPhotoFileIds()
        );
    }
}
