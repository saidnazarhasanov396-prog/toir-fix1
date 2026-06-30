package com.toir.dto.maintenanceworkspace;

import java.time.Instant;
import java.util.UUID;

public record MaintenancePlannerScheduleRequest(
        UUID workOrderId,
        Instant scheduledStart,
        Instant scheduledEnd,
        String plannerComment,
        Boolean freezeSchedule,
        Boolean breakInEmergency
) {}
