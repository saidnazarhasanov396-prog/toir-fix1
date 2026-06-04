package com.toir.entity.equipment;
import com.toir.entity.BaseEntity;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentLocationType;
import com.toir.enums.EquipmentOutsideReason;
import com.toir.enums.EquipmentStatus;

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

    @Column(name = "warranty_start_date")
    private LocalDate warrantyStartDate;

    @Column(name = "warranty_end_date")
    private LocalDate warrantyEndDate;

    @Column(name = "average_operating_life_hours")
    private Long averageOperatingLifeHours;

    @Column(name = "operation_start_date")
    private LocalDate operationStartDate;

    @Column(name = "expected_lifetime_months")
    private Integer expectedLifetimeMonths;

    @Column(name = "expected_lifetime_years")
    private Integer expectedLifetimeYears;

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
