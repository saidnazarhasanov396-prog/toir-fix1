package com.toir.dto.maintenanceplanning;

import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.enums.MeterType;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MaintenanceDueStructuredExplanationDto(
        String baseSource,
        Instant baseDate,
        Instant lastCompletionDate,
        MeterType meterType,
        Double currentMeterValue,
        Double intervalMeterValue,
        Double remainingMeterValue,
        Integer intervalDays,
        Integer intervalMonths,
        Integer toleranceDays,
        MaintenanceTriggerPolicy triggerPolicy,
        String reasonText,
        String blockingCode,
        String blockingField,
        String fixLink,
        String reasonCode,
        String reasonKey,
        Map<String, Object> reasonParams,
        List<String> supportingReasonKeys,
        List<Map<String, Object>> supportingReasonParams
) {}
