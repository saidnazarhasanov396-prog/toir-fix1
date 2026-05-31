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
        String recordedByUserName,
        String meterName,
        String equipmentName,
        String deviceId,
        String note,
        Instant createdAt
) {
    public static MeterReadingDto from(MeterReading r) {
        return new MeterReadingDto(
                r.getId(), r.getMeterId(), r.getEquipmentId(), r.getValue(), r.getDelta(),
                r.getReadAt(), r.getSource(), r.getRecordedByUserId(), null, null, null,
                r.getDeviceId(), r.getNote(), r.getCreatedAt()
        );
    }

    public static MeterReadingDto from(MeterReading r, String recordedByUserName, String meterName, String equipmentName) {
        return new MeterReadingDto(
                r.getId(), r.getMeterId(), r.getEquipmentId(), r.getValue(), r.getDelta(),
                r.getReadAt(), r.getSource(), r.getRecordedByUserId(), recordedByUserName, meterName, equipmentName,
                r.getDeviceId(), r.getNote(), r.getCreatedAt()
        );
    }
}
