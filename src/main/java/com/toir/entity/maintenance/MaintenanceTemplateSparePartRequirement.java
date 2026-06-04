package com.toir.entity.maintenance;

import com.toir.entity.BaseEntity;
import com.toir.entity.SparePart;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "maintenance_template_spare_part_requirements")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MaintenanceTemplateSparePartRequirement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private MaintenanceTemplate template;

    @Column(name = "template_id", nullable = false, insertable = false, updatable = false)
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

    @Column(nullable = false)
    private double quantity;

    @Column(nullable = false)
    private String unit;

    private String criticality;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
