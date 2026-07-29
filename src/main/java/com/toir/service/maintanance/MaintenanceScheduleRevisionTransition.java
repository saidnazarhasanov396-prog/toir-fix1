package com.toir.service.maintanance;

import com.toir.enums.MaintenanceScheduleContentHashVersion;
import java.util.Objects;

public record MaintenanceScheduleRevisionTransition(
        Long previousRevision,
        long nextRevision,
        MaintenanceScheduleContentHashVersion hashVersion,
        String calculatedHash
) {

    public MaintenanceScheduleRevisionTransition {
        Objects.requireNonNull(hashVersion, "hashVersion");
        Objects.requireNonNull(calculatedHash, "calculatedHash");
    }
}
