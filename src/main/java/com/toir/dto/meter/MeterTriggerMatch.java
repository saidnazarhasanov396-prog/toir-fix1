package com.toir.dto.meter;

import com.toir.entity.MeterType;

import java.util.UUID;

public record MeterTriggerMatch(
        UUID regulationId,
        String regulationCode,
        String regulationName,
        UUID meterId,
        MeterType meterType,
        double currentValue,
        double interval,
        double remaining,
        boolean due
) {}
