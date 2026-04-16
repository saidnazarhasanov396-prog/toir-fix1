package com.toir.dto.oee;

import com.toir.entity.OeeRecord;

import java.time.Instant;
import java.util.UUID;

public record OeeRecordDto(
        UUID id,
        UUID equipmentId,
        Instant shiftStart,
        Instant shiftEnd,
        double plannedProductionMinutes,
        double runMinutes,
        double idealCycleSeconds,
        double totalCount,
        double goodCount,
        double availability,
        double performance,
        double quality,
        double oee,
        String notes
) {
    public static OeeRecordDto from(OeeRecord r) {
        return new OeeRecordDto(
                r.getId(), r.getEquipmentId(), r.getShiftStart(), r.getShiftEnd(),
                r.getPlannedProductionMinutes(), r.getRunMinutes(), r.getIdealCycleSeconds(),
                r.getTotalCount(), r.getGoodCount(),
                r.getAvailability(), r.getPerformance(), r.getQuality(), r.getOee(), r.getNotes());
    }
}
