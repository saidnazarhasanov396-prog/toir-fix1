package com.toir.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class MaintenanceScheduleApprovalStaleException extends RestException {

    private final Reason reason;

    public MaintenanceScheduleApprovalStaleException(Reason reason) {
        super(message(reason), HttpStatus.CONFLICT, code(reason));
        this.reason = reason;
    }

    private static String message(Reason reason) {
        return switch (reason) {
            case REVISION_MISMATCH ->
                    "Approval targets a stale calculation revision";
            case HASH_MISMATCH ->
                    "Approval targets stale calculation content";
            case TARGET_MISMATCH ->
                    "Approval target does not match the PPR plan";
        };
    }

    private static String code(Reason reason) {
        return switch (reason) {
            case REVISION_MISMATCH ->
                    "PPR_CALCULATION_APPROVAL_REVISION_STALE";
            case HASH_MISMATCH ->
                    "PPR_CALCULATION_APPROVAL_HASH_STALE";
            case TARGET_MISMATCH ->
                    "PPR_CALCULATION_APPROVAL_TARGET_MISMATCH";
        };
    }

    public enum Reason {
        REVISION_MISMATCH,
        HASH_MISMATCH,
        TARGET_MISMATCH
    }
}
