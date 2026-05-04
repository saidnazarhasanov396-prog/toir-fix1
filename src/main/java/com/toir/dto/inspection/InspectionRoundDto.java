package com.toir.dto.inspection;

import com.toir.entity.inspection.InspectionRound;
import com.toir.enums.InspectionRoundStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InspectionRoundDto(
        UUID id,
        UUID routeId,
        UUID performedBy,
        Instant startedAt,
        Instant completedAt,
        InspectionRoundStatus status,
        int findingsCount,
        int alarmCount,
        String notes,
        List<InspectionRoundResultDto> results
) {
    public static InspectionRoundDto from(InspectionRound r) {
        return new InspectionRoundDto(
                r.getId(),
                r.getRoute() != null ? r.getRoute().getId() : null,
                r.getPerformedBy(),
                r.getStartedAt(),
                r.getCompletedAt(),
                r.getStatus(),
                r.getFindingsCount(),
                r.getAlarmCount(),
                r.getNotes(),
                r.getResults() == null ? List.of()
                        : r.getResults().stream().map(InspectionRoundResultDto::from).toList()
        );
    }
}
