package com.toir.exception;

public final class SparePartLifecycleErrorCodes {
    private SparePartLifecycleErrorCodes() { }

    public static final String LIFECYCLE_BLOCKED = "EQUIPMENT_SPARE_PART_LIFECYCLE_BLOCKED";
    public static final String OVERRIDE_REASON_MISSING = "SPARE_PART_LIFECYCLE_OVERRIDE_REASON_MISSING";
    public static final String OVERRIDE_FORBIDDEN = "SPARE_PART_LIFECYCLE_OVERRIDE_FORBIDDEN";
    public static final String RETURN_TO_STOCK_UNSUPPORTED = "RETURN_TO_STOCK_UNSUPPORTED";
    public static final String UNKNOWN_DISPOSITION_FORBIDDEN = "UNKNOWN_DISPOSITION_FORBIDDEN";
    public static final String MANUAL_DUE_REASON_MISSING = "SPARE_PART_MANUAL_DUE_REASON_MISSING";
    public static final String MANUAL_DUE_NOT_ALLOWED = "SPARE_PART_MANUAL_DUE_NOT_ALLOWED";
    public static final String DUE_EVENT_WORK_ORDER_CONFLICT = "SPARE_PART_DUE_EVENT_WORK_ORDER_CONFLICT";
}
