package com.toir.entity.equipment;
import com.toir.entity.BaseEntity;
import com.toir.enums.MeterSource;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "meter_readings", indexes = {
        @Index(name = "idx_meter_readings_meter", columnList = "meter_id,read_at"),
        @Index(name = "idx_meter_readings_equipment", columnList = "equipment_id,read_at")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MeterReading extends BaseEntity {

    @Column(name = "meter_id", nullable = false)
    private UUID meterId;

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(nullable = false)
    private double value;

    @Column(name = "delta")
    private Double delta;

    @Column(name = "read_at", nullable = false)
    private Instant readAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MeterSource source;

    @Column(name = "recorded_by_user_id")
    private UUID recordedByUserId;

    @Column(name = "device_id")
    private String deviceId;

    @Column(columnDefinition = "text")
    private String note;
}
