package com.toir.enums.equipmentlifecycleexport;

public enum EquipmentLifecycleExportStatus {
    QUEUED,
    PREPARING,
    RUNNING,
    FINALIZING,
    COMPLETED,
    CANCEL_REQUESTED,
    CANCELLED,
    FAILED,
    EXPIRED;

    public boolean terminal() {
        return this == COMPLETED || this == CANCELLED || this == EXPIRED;
    }

    public boolean active() {
        return this == QUEUED || this == PREPARING || this == RUNNING
                || this == FINALIZING || this == CANCEL_REQUESTED;
    }
}
