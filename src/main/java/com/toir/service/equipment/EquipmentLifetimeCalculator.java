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
}
