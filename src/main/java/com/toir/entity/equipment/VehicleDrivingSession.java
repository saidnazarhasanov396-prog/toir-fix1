package com.toir.entity.equipment;

import com.toir.entity.BaseEntity;
import com.toir.enums.VehicleDrivingSessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vehicle_driving_sessions")
@Getter
@Setter
public class VehicleDrivingSession extends BaseEntity {

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "driver_employee_id", nullable = false)
    private UUID driverEmployeeId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Column(name = "start_odometer_km")
    private Double startOdometerKm;

    @Column(name = "end_odometer_km")
    private Double endOdometerKm;

    @Column(name = "start_engine_hours")
    private Double startEngineHours;

    @Column(name = "end_engine_hours")
    private Double endEngineHours;

    @Column(name = "issued_by")
    private UUID issuedBy;

    @Column(name = "returned_by")
    private UUID returnedBy;

    @Column(columnDefinition = "text")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VehicleDrivingSessionStatus status = VehicleDrivingSessionStatus.OPEN;
}
