package com.toir.dto.calibration;

import com.toir.entity.CalibrationRecord;

import java.time.LocalDate;
import java.util.UUID;

public record CalibrationRecordDto(
        UUID id,
        UUID equipmentId,
        String certificateNumber,
        String performedBy,
        LocalDate performedAt,
        LocalDate nextDueAt,
        String result,
        Double tolerance,
        Double measuredError,
        String unit,
        UUID documentFileId,
        String notes,
        Integer daysUntilDue
) {
    public static CalibrationRecordDto from(CalibrationRecord c) {
        Integer days = null;
        if (c.getNextDueAt() != null) {
            days = (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), c.getNextDueAt());
        }
        return new CalibrationRecordDto(
                c.getId(), c.getEquipmentId(), c.getCertificateNumber(), c.getPerformedBy(),
                c.getPerformedAt(), c.getNextDueAt(), c.getResult(), c.getTolerance(),
                c.getMeasuredError(), c.getUnit(), c.getDocumentFileId(), c.getNotes(), days
        );
    }
}
