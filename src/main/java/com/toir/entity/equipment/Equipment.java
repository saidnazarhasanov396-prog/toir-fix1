package com.toir.entity.equipment;
import com.toir.entity.BaseEntity;
import com.toir.enums.EquipmentCategory;
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
    private UUID departmentId;

    @Column(name = "location_id")
    private UUID locationId;

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

    @Column(name = "warranty_until")
    private LocalDate warrantyUntil;

    private String description;

}
