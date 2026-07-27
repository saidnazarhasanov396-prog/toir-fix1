package com.toir.dto.maintenanceschedule;

import com.toir.enums.MaintenanceScheduleAnchorMode;
import com.toir.enums.MaintenanceScheduleScopeType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MaintenanceSchedulePreviewRequest(
        @NotNull LocalDate fromDate,
        @NotNull LocalDate toDate,
        @NotNull MaintenanceScheduleScopeType scopeType,
        List<UUID> equipmentIds,
        List<UUID> equipmentTypeIds,
        UUID departmentId,
        @NotNull MaintenanceScheduleAnchorMode anchorMode
) {}
