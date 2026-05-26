package com.toir.entity.maintenance;

import com.toir.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "maintenance_actions")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MaintenanceAction extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    private String category;

    @Column(name = "default_duration_hours")
    private Double defaultDurationHours;

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

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
