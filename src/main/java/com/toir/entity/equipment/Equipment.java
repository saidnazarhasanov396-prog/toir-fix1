package com.toir.entity.equipment;
import com.toir.entity.BaseEntity;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentLocationType;
import com.toir.enums.EquipmentOutsideReason;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.MeterType;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "equipment")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Equipment extends BaseEntity {

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "inventory_number", nullable = false)
    private String inventoryNumber;

    @Column(name = "technical_number", unique = true)
    private String technicalNumber;

    @Column(name = "serial_number")
    private String serialNumber;

    private String model;

    @Column(name = "produced_year")
    private Integer producedYear;

    @Column(name = "equipment_type_id", nullable = false)
    private UUID equipmentTypeId;

    @Column(name = "department_id")
    // Physical department where equipment is currently installed.
    // PBAC ownership/scope is stored in responsibleDepartmentId.
    private UUID departmentId;

    @Column(name = "location_id")
    // Physical location reference only. Must not store warehouse id.
    private UUID locationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_location_type")
    private EquipmentLocationType currentLocationType;

    @Column(name = "current_warehouse_id")
    private UUID currentWarehouseId;

    @Column(name = "responsible_department_id")
    private UUID responsibleDepartmentId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "criticality_class_id")
    private UUID criticalityClassId;

    @Column(name = "responsible_id")
    private UUID responsibleId;

    @Column(name = "manufacturer")
    private String manufacturer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EquipmentStatus status = EquipmentStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EquipmentCategory category = EquipmentCategory.PRODUCTION_EQUIPMENT;

    @Column(name = "commissioned_at")
    private LocalDate commissionedAt;

    @Column(name = "arrival_date")
    private LocalDate arrivalDate;

    @Column(name = "warranty_until")
    private LocalDate warrantyUntil;

    @Column(name = "has_warranty", nullable = false)
    @Builder.Default
    private Boolean hasWarranty = false;

    @Column(name = "warranty_attachment_id")
    private UUID warrantyAttachmentId;

    @Column(name = "procurement_request_id")
    private UUID procurementRequestId;

    @Column(name = "procurement_request_line_id")
    private UUID procurementRequestLineId;

    @Column(name = "procurement_stock_movement_id")
    private UUID procurementStockMovementId;

    @Column(name = "warranty_start_date")
    private LocalDate warrantyStartDate;

    @Column(name = "warranty_end_date")
    private LocalDate warrantyEndDate;

    @Column(name = "average_operating_life_hours")
    private Long averageOperatingLifeHours;

    @Column(name = "average_daily_usage")
    private Double averageDailyUsage;

    @Column(name = "operation_start_date")
    private LocalDate operationStartDate;

    @Column(name = "expected_lifetime_months")
    private Integer expectedLifetimeMonths;

    @Column(name = "expected_lifetime_years")
    private Integer expectedLifetimeYears;

    @Column(name = "expected_lifetime_hours")
    private Long expectedLifetimeHours;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifetime_counter_type")
    private MeterType lifetimeCounterType;

    @Column(name = "lifetime_meter_id")
    private UUID lifetimeMeterId;

    @Column(name = "lifetime_limit_value")
    private Double lifetimeLimitValue;

    @Column(name = "days_of_resource_remaining")
    private Long daysOfResourceRemaining;

    @Column(name = "forecast_consumed_resource")
    private Double forecastConsumedResource;

    @Column(name = "forecast_remaining_resource")
    private Double forecastRemainingResource;

    @Column(name = "forecast_avg_usage_per_active_day")
    private Double forecastAvgUsagePerActiveDay;

    @Column(name = "forecast_remaining_active_days")
    private Long forecastRemainingActiveDays;

    @Column(name = "forecast_calculated_at")
    private LocalDate forecastCalculatedAt;

    @Column(name = "lifetime_baseline_value")
    private Double lifetimeBaselineValue;

    @Column(name = "lifetime_warning_percent")
    private Double lifetimeWarningPercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "outside_reason")
    private EquipmentOutsideReason outsideReason;

    @Column(name = "outside_taken_by")
    private String outsideTakenBy;

    @Column(name = "outside_recipient_user_id")
    private UUID outsideRecipientUserId;

    @Column(name = "outside_started_date")
    private LocalDate outsideStartedDate;

    @Column(name = "outside_expected_return_date")
    private LocalDate outsideExpectedReturnDate;

    @Column(name = "outside_destination")
    private String outsideDestination;

    @Column(name = "outside_reason_note", columnDefinition = "text")
    private String outsideReasonNote;

    private String description;

}
