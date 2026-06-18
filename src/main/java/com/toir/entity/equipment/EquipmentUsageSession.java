package com.toir.entity.equipment;

import com.toir.entity.BaseEntity;
import com.toir.enums.EquipmentUsageSessionStatus;
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
@Table(name = "equipment_usage_sessions")
@Getter
@Setter
public class EquipmentUsageSession extends BaseEntity {

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "operator_employee_id", nullable = false)
    private UUID operatorEmployeeId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Column(name = "meter_id")
    private UUID meterId;

    @Column(name = "start_meter_value")
    private Double startMeterValue;

    @Column(name = "end_meter_value")
    private Double endMeterValue;

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
    private EquipmentUsageSessionStatus status = EquipmentUsageSessionStatus.OPEN;
}
