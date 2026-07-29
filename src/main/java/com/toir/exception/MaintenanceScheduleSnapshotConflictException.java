package com.toir.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class MaintenanceScheduleSnapshotConflictException extends RestException {

    private final Reason reason;

    public MaintenanceScheduleSnapshotConflictException(Reason reason) {
        super(message(reason), HttpStatus.CONFLICT, errorCode(reason));
        this.reason = reason;
    }

    private static String message(Reason reason) {
        return switch (reason) {
            case REVISION_ALREADY_EXISTS ->
                    "Maintenance schedule calculation revision already exists";
            case DUPLICATE_SOURCE_ITEM_KEY ->
                    "Maintenance schedule snapshot contains duplicate source item keys";
        };
    }

    private static String errorCode(Reason reason) {
        return "MAINTENANCE_SCHEDULE_SNAPSHOT_" + reason.name();
    }

    public enum Reason {
        REVISION_ALREADY_EXISTS,
        DUPLICATE_SOURCE_ITEM_KEY
    }
}
