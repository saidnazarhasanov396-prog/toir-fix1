package com.toir.dto.financialreview;

public record EvaluateOverdueActualCostsResponse(
        int thresholdHours,
        int reminderWindowHours,
        int scanned,
        int overdueCount,
        int dueSoonCount,
        int createdNotifications,
        int createdReminderNotifications,
        int skipped,
        int skippedReminders
) {
}
