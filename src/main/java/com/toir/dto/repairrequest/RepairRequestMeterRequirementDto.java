package com.toir.dto.repairrequest;

import com.toir.enums.MeterType;

import java.time.Instant;
import java.util.UUID;

public record RepairRequestMeterRequirementDto(
        UUID meterId,
        MeterType meterType,
        String meterName,
        String unit,
        double currentValue,
        boolean required,
        boolean provided,
        Double latestValue,
        Instant latestReadAt
) {}
