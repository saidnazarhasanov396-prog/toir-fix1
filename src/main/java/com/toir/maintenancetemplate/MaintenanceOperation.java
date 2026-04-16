package com.toir.maintenancetemplate;

import com.toir.common.jpa.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "maintenance_operations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"template_id", "sequence"}))
public class MaintenanceOperation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private MaintenanceTemplate template;

    @Column(nullable = false)
    private int sequence;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "duration_hours", nullable = false)
    private double durationHours;

    @Column(name = "required_skill")
    private String requiredSkill;

    @Column(name = "safety_notes", columnDefinition = "text")
    private String safetyNotes;

    @Column(name = "tools_required", columnDefinition = "text")
    private String toolsRequired;

    @Column(name = "spare_parts_required", columnDefinition = "text")
    private String sparePartsRequired;

    @Column(name = "consumables_required", columnDefinition = "text")
    private String consumablesRequired;

    @Column(name = "control_parameter")
    private String controlParameter;

    @Column(name = "control_unit")
    private String controlUnit;

    @Column(name = "control_min")
    private Double controlMin;

    @Column(name = "control_max")
    private Double controlMax;

    @Column(name = "instruction_url")
    private String instructionUrl;

    public String getToolsRequired() { return toolsRequired; }
    public void setToolsRequired(String toolsRequired) { this.toolsRequired = toolsRequired; }
    public String getSparePartsRequired() { return sparePartsRequired; }
    public void setSparePartsRequired(String sparePartsRequired) { this.sparePartsRequired = sparePartsRequired; }
    public String getConsumablesRequired() { return consumablesRequired; }
    public void setConsumablesRequired(String consumablesRequired) { this.consumablesRequired = consumablesRequired; }
    public String getControlParameter() { return controlParameter; }
    public void setControlParameter(String controlParameter) { this.controlParameter = controlParameter; }
    public String getControlUnit() { return controlUnit; }
    public void setControlUnit(String controlUnit) { this.controlUnit = controlUnit; }
    public Double getControlMin() { return controlMin; }
    public void setControlMin(Double controlMin) { this.controlMin = controlMin; }
    public Double getControlMax() { return controlMax; }
    public void setControlMax(Double controlMax) { this.controlMax = controlMax; }
    public String getInstructionUrl() { return instructionUrl; }
    public void setInstructionUrl(String instructionUrl) { this.instructionUrl = instructionUrl; }

    public MaintenanceTemplate getTemplate() { return template; }
    public void setTemplate(MaintenanceTemplate template) { this.template = template; }
    public int getSequence() { return sequence; }
    public void setSequence(int sequence) { this.sequence = sequence; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public double getDurationHours() { return durationHours; }
    public void setDurationHours(double durationHours) { this.durationHours = durationHours; }
    public String getRequiredSkill() { return requiredSkill; }
    public void setRequiredSkill(String requiredSkill) { this.requiredSkill = requiredSkill; }
    public String getSafetyNotes() { return safetyNotes; }
    public void setSafetyNotes(String safetyNotes) { this.safetyNotes = safetyNotes; }
}
