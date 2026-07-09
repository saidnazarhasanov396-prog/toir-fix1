package com.toir.enums;

/**
 * Machine-readable reason behind a {@link MaintenanceDueStatus} calculation. Paired with
 * {@code reasonKey}/{@code reasonParams} on {@code MaintenanceDueStructuredExplanationDto} so the
 * frontend can render a localized sentence instead of parsing the legacy English {@code explanation}
 * text.
 */
public enum MaintenanceDueReasonCode {
    MANUAL_TRIGGER_POLICY,
    NO_TRIGGER_CONFIGURED,
    NO_CALENDAR_ANCHOR,
    CALENDAR_OVERDUE,
    CALENDAR_DUE,
    CALENDAR_UPCOMING,
    CALENDAR_NOT_DUE,
    REQUIRE_INITIAL_ANCHOR,
    NO_COMPLETION_ANCHOR,
    MISSING_ACTIVE_METER,
    METER_OVERDUE,
    METER_DUE,
    METER_UPCOMING,
    METER_NOT_DUE,
    WAITING_ALL_UPCOMING,
    WAITING_ALL_NONE_DUE
}
