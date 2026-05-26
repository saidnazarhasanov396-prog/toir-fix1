package com.toir.entity.maintenance;
import com.toir.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "maintenance_operations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"template_id", "sequence"}))
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MaintenanceOperation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private MaintenanceTemplate template;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "action_id")
    private MaintenanceAction action;

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

}
