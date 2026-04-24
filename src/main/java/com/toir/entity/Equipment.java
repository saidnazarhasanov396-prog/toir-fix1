package com.toir.entity;
import com.toir.enums.EquipmentStatus;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "equipment")
public class Equipment extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "inventory_number", nullable = false, unique = true)
    private String inventoryNumber;

    @Column(name = "technical_number", unique = true)
    private String technicalNumber;

    @Column(name = "serial_number")
    private String serialNumber;

    private String model;

    @Column(name = "equipment_type_id", nullable = false)
    private UUID equipmentTypeId;

    @Column(name = "department_id", nullable = false)
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

    @Column(name = "commissioned_at")
    private LocalDate commissionedAt;

    @Column(name = "warranty_until")
    private LocalDate warrantyUntil;

    private String description;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getInventoryNumber() { return inventoryNumber; }
    public void setInventoryNumber(String inventoryNumber) { this.inventoryNumber = inventoryNumber; }
    public String getTechnicalNumber() { return technicalNumber; }
    public void setTechnicalNumber(String technicalNumber) { this.technicalNumber = technicalNumber; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public UUID getEquipmentTypeId() { return equipmentTypeId; }
    public void setEquipmentTypeId(UUID equipmentTypeId) { this.equipmentTypeId = equipmentTypeId; }
    public UUID getDepartmentId() { return departmentId; }
    public void setDepartmentId(UUID departmentId) { this.departmentId = departmentId; }
    public UUID getLocationId() { return locationId; }
    public void setLocationId(UUID locationId) { this.locationId = locationId; }
    public UUID getParentId() { return parentId; }
    public void setParentId(UUID parentId) { this.parentId = parentId; }
    public UUID getCriticalityClassId() { return criticalityClassId; }
    public void setCriticalityClassId(UUID criticalityClassId) { this.criticalityClassId = criticalityClassId; }
    public UUID getResponsibleId() { return responsibleId; }
    public void setResponsibleId(UUID responsibleId) { this.responsibleId = responsibleId; }
    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
    public EquipmentStatus getStatus() { return status; }
    public void setStatus(EquipmentStatus status) { this.status = status; }
    public LocalDate getCommissionedAt() { return commissionedAt; }
    public void setCommissionedAt(LocalDate commissionedAt) { this.commissionedAt = commissionedAt; }
    public LocalDate getWarrantyUntil() { return warrantyUntil; }
    public void setWarrantyUntil(LocalDate warrantyUntil) { this.warrantyUntil = warrantyUntil; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
