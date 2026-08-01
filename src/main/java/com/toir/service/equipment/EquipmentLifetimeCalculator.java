package com.toir.service.equipment;

public final class EquipmentLifetimeCalculator {

    private EquipmentLifetimeCalculator() {
    }

    public static Long remainingDays(Double remainingValue, Double averageDailyUsage) {
        if (remainingValue == null || averageDailyUsage == null
                || !Double.isFinite(remainingValue) || !Double.isFinite(averageDailyUsage)
                || averageDailyUsage <= 0) {
            return null;
        }
        return (long) Math.floor(Math.max(remainingValue, 0.0) / averageDailyUsage);
    }
    public static Long remainingDays(
            Double baselineValue,
            Double limitValue,
            Double currentValue,
            Double averageDailyUsage
    ) {
        if (baselineValue == null || limitValue == null || currentValue == null
                || !Double.isFinite(baselineValue) || !Double.isFinite(limitValue)
                || !Double.isFinite(currentValue) || limitValue <= 0) {
            return null;
        }
        return remainingDays(baselineValue + limitValue - currentValue, averageDailyUsage);
    }
}
