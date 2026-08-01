package com.toir.service.equipment;

import com.toir.exception.RestException;

public final class EquipmentLifetimeValidator {

    private EquipmentLifetimeValidator() {
    }

    public static void validate(
            Double limitValue,
            Double baselineValue,
            Double warningPercent,
            Double averageDailyUsage,
            Double currentValue
    ) {
        if (limitValue != null && (!Double.isFinite(limitValue) || limitValue <= 0)) {
            throw RestException.badRequest("Lifetime limit must be positive");
        }
        if (baselineValue != null && (!Double.isFinite(baselineValue) || baselineValue < 0)) {
            throw RestException.badRequest("Lifetime baseline must be zero or positive");
        }
        if (warningPercent != null && (!Double.isFinite(warningPercent)
                || warningPercent <= 0 || warningPercent > 100)) {
            throw RestException.badRequest("Lifetime warning percent must be greater than 0 and at most 100");
        }
        if (averageDailyUsage != null && (!Double.isFinite(averageDailyUsage) || averageDailyUsage <= 0)) {
            throw RestException.badRequest("Average daily usage must be positive");
        }
        if (baselineValue != null && currentValue != null
                && (!Double.isFinite(currentValue) || currentValue < baselineValue)) {
            throw RestException.badRequest("Current reading cannot be below lifetime baseline");
        }
    }
}
