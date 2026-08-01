package com.toir.repository.inspection;

import java.time.Instant;
import java.util.UUID;

public interface EquipmentInspectionLifecycleProjection {
    UUID getSourceId();
    UUID getRoundId();
    UUID getCheckpointId();
    String getRoundStatus();
    String getResultStatus();
    Instant getStartedAt();
    Instant getCompletedAt();
    Double getMeasuredValue();
    String getMeasuredUnit();
    Double getExpectedMin();
    Double getExpectedMax();
    String getExpectedUnit();
    UUID getDefectId();
    Instant getSourceUpdatedAt();
}
