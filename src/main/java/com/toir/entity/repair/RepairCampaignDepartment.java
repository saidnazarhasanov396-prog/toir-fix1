package com.toir.entity.repair;

import com.toir.entity.BaseEntity;
import com.toir.enums.RepairCampaignDepartmentRole;
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

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "repair_campaign_departments")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RepairCampaignDepartment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private RepairCampaign campaign;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepairCampaignDepartmentRole role = RepairCampaignDepartmentRole.PARTICIPANT;

    @Column(name = "planned_budget", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal plannedBudget = BigDecimal.ZERO;

    @Column(columnDefinition = "text")
    private String notes;
}
