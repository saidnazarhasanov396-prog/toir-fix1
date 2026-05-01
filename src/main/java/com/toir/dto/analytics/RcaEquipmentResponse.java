package com.toir.dto.analytics;

import com.toir.enums.DefectStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RcaEquipmentResponse(
        String equipmentId,
        List<Incident> incidents,
        List<RcaOverviewResponse.CountRow> topCauses,
        List<TimelineEvent> timeline
) {
    public record Incident(
            UUID id,
            String code,
            String title,
            Instant detectedAt,
            DefectStatus status
    ) {
    }

    public record TimelineEvent(Instant occurredAt, String title, String description) {
    }
}
