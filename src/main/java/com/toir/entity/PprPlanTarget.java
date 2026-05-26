package com.toir.entity;

import com.toir.enums.PprTargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "ppr_plan_targets")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PprPlanTarget extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private PprPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private PprTargetType targetType;

    @Column(name = "equipment_id")
    private UUID equipmentId;

    @Column(name = "equipment_type_id")
    private UUID equipmentTypeId;
}
