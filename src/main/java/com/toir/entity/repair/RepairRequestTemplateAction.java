package com.toir.entity.repair;

import com.toir.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "repair_request_template_actions")
@Getter
@Setter
@NoArgsConstructor
public class RepairRequestTemplateAction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repair_request_id", nullable = false)
    private RepairRequest repairRequest;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "operation_id")
    private UUID operationId;

    @Column(name = "action_id")
    private UUID actionId;

    @Column(name = "specialist_id")
    private UUID specialistId;

    @Column(name = "specialisation_id")
    private UUID specialisationId;

    @Column(nullable = false)
    private int sequence;

    @Column(name = "custom_name")
    private String customName;

    @Column(name = "name_snapshot", nullable = false)
    private String nameSnapshot;

    @Column(name = "duration_hours")
    private Double durationHours;

    @Column(name = "required_skill")
    private String requiredSkill;
}
