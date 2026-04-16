package com.toir.sla;

import com.toir.common.jpa.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "sla_rules")
public class SlaRule extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false)
    private SlaEntityType entityType;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    private SlaTriggerType triggerType;

    @Column(name = "threshold_hours", nullable = false)
    private int thresholdHours;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public SlaEntityType getEntityType() { return entityType; }
    public void setEntityType(SlaEntityType entityType) { this.entityType = entityType; }
    public SlaTriggerType getTriggerType() { return triggerType; }
    public void setTriggerType(SlaTriggerType triggerType) { this.triggerType = triggerType; }
    public int getThresholdHours() { return thresholdHours; }
    public void setThresholdHours(int thresholdHours) { this.thresholdHours = thresholdHours; }
    public UUID getDepartmentId() { return departmentId; }
    public void setDepartmentId(UUID departmentId) { this.departmentId = departmentId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
