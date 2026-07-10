package com.toir.entity.equipment;
import com.toir.entity.BaseEntity;
import com.toir.enums.MeterType;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "equipment_meters",
        uniqueConstraints = @UniqueConstraint(columnNames = {"equipment_id", "meter_type", "name"}))
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EquipmentMeter extends BaseEntity {

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "meter_type", nullable = false)
    private MeterType meterType;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String unit;

    @Column(name = "current_value", nullable = false)
    private double currentValue;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    @Column(name = "rollover_value")
    private Double rolloverValue;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "is_primary", nullable = false)
    private boolean primary = false;

}
