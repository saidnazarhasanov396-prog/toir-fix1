package com.toir.entity;
import com.toir.enums.SlaEntityType;

import com.toir.enums.SlaTriggerType;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "sla_rules")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
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
}
