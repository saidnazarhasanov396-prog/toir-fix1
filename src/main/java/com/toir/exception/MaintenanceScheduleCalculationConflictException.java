package com.toir.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class MaintenanceScheduleCalculationConflictException extends RestException {

    private final Reason reason;

    public MaintenanceScheduleCalculationConflictException(Reason reason) {
        super(message(reason), status(reason), errorCode(reason));
        this.reason = reason;
    }

    private static HttpStatus status(Reason reason) {
        return switch (reason) {
            case HASH_VERSION_UNSUPPORTED, INVALID_HASH -> HttpStatus.BAD_REQUEST;
            case REVISION_CONFLICT, HASH_MISMATCH, INVALID_REVISION_TRANSITION ->
                    HttpStatus.CONFLICT;
        };
    }

    private static String message(Reason reason) {
        return switch (reason) {
            case REVISION_CONFLICT ->
                    "Maintenance schedule calculation revision conflicts with stored state";
            case HASH_MISMATCH ->
                    "Maintenance schedule calculation content does not match its binding";
            case HASH_VERSION_UNSUPPORTED ->
                    "Maintenance schedule calculation hash version is unsupported";
            case INVALID_HASH ->
                    "Maintenance schedule calculation hash is invalid";
            case INVALID_REVISION_TRANSITION ->
                    "Maintenance schedule calculation revision transition is invalid";
        };
    }

    private static String errorCode(Reason reason) {
        return switch (reason) {
            case REVISION_CONFLICT -> "PPR_CALCULATION_REVISION_CONFLICT";
            case HASH_MISMATCH -> "PPR_CALCULATION_HASH_MISMATCH";
            case HASH_VERSION_UNSUPPORTED ->
                    "PPR_CALCULATION_HASH_VERSION_UNSUPPORTED";
            case INVALID_HASH -> "PPR_CALCULATION_INVALID_HASH";
            case INVALID_REVISION_TRANSITION ->
                    "PPR_CALCULATION_INVALID_REVISION_TRANSITION";
        };
    }

    public enum Reason {
        REVISION_CONFLICT,
        HASH_MISMATCH,
        HASH_VERSION_UNSUPPORTED,
        INVALID_HASH,
        INVALID_REVISION_TRANSITION
    }
}
