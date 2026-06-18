package com.toir.entity.equipment;

import com.toir.entity.BaseEntity;
import com.toir.entity.UploadedFile;
import com.toir.enums.VehicleRegistrationPlateType;
import com.toir.enums.VehicleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "vehicle_details", indexes = {
        @Index(name = "idx_vehicle_details_equipment", columnList = "equipment_id"),
        @Index(name = "idx_vehicle_details_plate", columnList = "plate_number"),
        @Index(name = "idx_vehicle_details_vin", columnList = "vin")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VehicleDetails extends BaseEntity {

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "plate_number", nullable = false)
    private String plateNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "plate_type", nullable = false)
    private VehicleRegistrationPlateType plateType = VehicleRegistrationPlateType.UNKNOWN;

    @Column
    private String vin;

    private String brand;
    private String model;

    @Column(name = "manufacture_year")
    private Integer manufactureYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false)
    private VehicleType vehicleType;

    @Column(name = "body_number")
    private String bodyNumber;

    @Column(name = "chassis_number")
    private String chassisNumber;

    @Column(name = "engine_number")
    private String engineNumber;

    @Column(name = "fuel_type")
    private String fuelType;

    @Column(name = "fuel_tank_capacity")
    private Double fuelTankCapacity;

    @Column(name = "carrying_capacity")
    private Double carryingCapacity;

    @Column(name = "seat_count")
    private Integer seatCount;

    @Column(name = "assigned_driver_id")
    private UUID assignedDriverId;

    @Column(name = "assigned_driver_usage_limit_minutes")
    private Integer assignedDriverUsageLimitMinutes;

    @Column(name = "assigned_driver_assigned_by")
    private UUID assignedDriverAssignedBy;

    @Column(name = "assigned_driver_assigned_at")
    private Instant assignedDriverAssignedAt;

    @Column(name = "current_odometer_km", nullable = false)
    private double currentOdometerKm;

    @Column(name = "current_engine_hours", nullable = false)
    private double currentEngineHours;

    @Column(name = "registration_certificate_number")
    private String registrationCertificateNumber;

    @Column(name = "insurance_policy_number")
    private String insurancePolicyNumber;

    @Column(name = "insurance_expiry_date")
    private LocalDate insuranceExpiryDate;

    @Column(name = "technical_inspection_expiry_date")
    private LocalDate technicalInspectionExpiryDate;

    @Column(name = "gps_device_id")
    private String gpsDeviceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_file_id")
    private UploadedFile documentFile;
}
