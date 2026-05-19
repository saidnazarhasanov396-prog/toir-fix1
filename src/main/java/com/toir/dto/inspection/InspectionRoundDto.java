package com.toir.dto.inspection;

import com.toir.entity.inspection.InspectionRound;
import com.toir.enums.InspectionRoundStatus;
import jakarta.persistence.EntityNotFoundException;

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
                routeIdOrNull(r),
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

    public static InspectionRoundDto fromSummary(InspectionRound r) {
        return new InspectionRoundDto(
                r.getId(),
                routeIdOrNull(r),
                r.getPerformedBy(),
                r.getStartedAt(),
                r.getCompletedAt(),
                r.getStatus(),
                r.getFindingsCount(),
                r.getAlarmCount(),
                r.getNotes(),
                List.of()
        );
    }

    private static UUID routeIdOrNull(InspectionRound r) {
        if (r.getRoute() == null) {
            return null;
        }
        try {
            return r.getRoute().getId();
        } catch (EntityNotFoundException ex) {
            return null;
        }
    }
}
