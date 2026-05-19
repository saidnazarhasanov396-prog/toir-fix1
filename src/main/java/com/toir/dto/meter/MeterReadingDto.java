package com.toir.dto.meter;

import com.toir.entity.equipment.MeterReading;
import com.toir.enums.MeterSource;

import java.time.Instant;
import java.util.UUID;

public record MeterReadingDto(
        UUID id,
        UUID meterId,
        UUID equipmentId,
        double value,
        Double delta,
        Instant readAt,
        MeterSource source,
        UUID recordedByUserId,
        String deviceId,
        String note
) {
    public static MeterReadingDto from(MeterReading r) {
        return new MeterReadingDto(
                r.getId(), r.getMeterId(), r.getEquipmentId(), r.getValue(), r.getDelta(),
                r.getReadAt(), r.getSource(), r.getRecordedByUserId(), r.getDeviceId(), r.getNote()
        );
    }
}
