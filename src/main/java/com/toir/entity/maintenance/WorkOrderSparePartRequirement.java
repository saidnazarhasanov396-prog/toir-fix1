package com.toir.entity.maintenance;

import com.toir.entity.BaseEntity;
import com.toir.entity.SparePart;
import com.toir.enums.WorkOrderSparePartRequirementSourceType;
import com.toir.enums.WorkOrderSparePartRequirementStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "work_order_spare_part_requirements")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WorkOrderSparePartRequirement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @Column(name = "work_order_id", nullable = false, insertable = false, updatable = false)
    private UUID workOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private WorkOrderSparePartRequirementSourceType sourceType =
            WorkOrderSparePartRequirementSourceType.TEMPLATE_REQUIRED_SPARE_PART;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_requirement_id")
    private MaintenanceTemplateSparePartRequirement sourceRequirement;

    @Column(name = "source_requirement_id", insertable = false, updatable = false)
    private UUID sourceRequirementId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private MaintenanceTemplate template;

    @Column(name = "template_id", insertable = false, updatable = false)
    private UUID templateId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operation_id")
    private MaintenanceOperation operation;

    @Column(name = "operation_id", insertable = false, updatable = false)
    private UUID operationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spare_part_id", nullable = false)
    private SparePart sparePart;

    @Column(name = "spare_part_id", nullable = false, insertable = false, updatable = false)
    private UUID sparePartId;

    @Column(name = "required_qty", nullable = false)
    private double requiredQty;

    @Column(nullable = false)
    private String unit;

    private String criticality;

    @Column(columnDefinition = "text")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkOrderSparePartRequirementStatus status = WorkOrderSparePartRequirementStatus.PLANNED;
}
