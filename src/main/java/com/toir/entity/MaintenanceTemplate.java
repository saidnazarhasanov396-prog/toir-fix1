package com.toir.entity;
import com.toir.entity.MaintenanceOperation;

import com.toir.entity.BaseEntity;
import com.toir.entity.MaintenanceKind;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "maintenance_templates")
public class MaintenanceTemplate extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "equipment_type_id", nullable = false)
    private UUID equipmentTypeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "maintenance_kind", nullable = false)
    private MaintenanceKind maintenanceKind;

    @Column(name = "normative_labor_hours", nullable = false)
    private double normativeLaborHours;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    private List<MaintenanceOperation> operations = new ArrayList<>();

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public UUID getEquipmentTypeId() { return equipmentTypeId; }
    public void setEquipmentTypeId(UUID equipmentTypeId) { this.equipmentTypeId = equipmentTypeId; }
    public MaintenanceKind getMaintenanceKind() { return maintenanceKind; }
    public void setMaintenanceKind(MaintenanceKind maintenanceKind) { this.maintenanceKind = maintenanceKind; }
    public double getNormativeLaborHours() { return normativeLaborHours; }
    public void setNormativeLaborHours(double normativeLaborHours) { this.normativeLaborHours = normativeLaborHours; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public List<MaintenanceOperation> getOperations() { return operations; }
    public void setOperations(List<MaintenanceOperation> operations) { this.operations = operations; }
}
