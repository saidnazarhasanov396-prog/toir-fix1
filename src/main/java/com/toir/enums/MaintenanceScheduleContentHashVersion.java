package com.toir.enums;

import com.toir.exception.MaintenanceScheduleCalculationConflictException;
import com.toir.exception.MaintenanceScheduleCalculationConflictException.Reason;

public enum MaintenanceScheduleContentHashVersion {
    V1(1);

    private final int persistedValue;

    MaintenanceScheduleContentHashVersion(int persistedValue) {
        this.persistedValue = persistedValue;
    }

    public int persistedValue() {
        return persistedValue;
    }

    public static MaintenanceScheduleContentHashVersion fromPersistedValue(
            Integer persistedValue) {
        if (persistedValue != null && persistedValue == V1.persistedValue) {
            return V1;
        }
        throw new MaintenanceScheduleCalculationConflictException(
                Reason.HASH_VERSION_UNSUPPORTED);
    }
}
