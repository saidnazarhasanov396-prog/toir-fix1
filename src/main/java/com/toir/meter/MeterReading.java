package com.toir.meter;

import com.toir.common.jpa.BaseEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "meter_readings", indexes = {
        @Index(name = "idx_meter_readings_meter", columnList = "meter_id,read_at"),
        @Index(name = "idx_meter_readings_equipment", columnList = "equipment_id,read_at")
})
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

    public UUID getMeterId() { return meterId; }
    public void setMeterId(UUID meterId) { this.meterId = meterId; }
    public UUID getEquipmentId() { return equipmentId; }
    public void setEquipmentId(UUID equipmentId) { this.equipmentId = equipmentId; }
    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }
    public Double getDelta() { return delta; }
    public void setDelta(Double delta) { this.delta = delta; }
    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }
    public MeterSource getSource() { return source; }
    public void setSource(MeterSource source) { this.source = source; }
    public UUID getRecordedByUserId() { return recordedByUserId; }
    public void setRecordedByUserId(UUID recordedByUserId) { this.recordedByUserId = recordedByUserId; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
