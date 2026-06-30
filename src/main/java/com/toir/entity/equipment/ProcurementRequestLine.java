package com.toir.entity.equipment;

import com.toir.entity.BaseEntity;
import com.toir.entity.projects.ProcurementRequest;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "procurement_request_lines")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProcurementRequestLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private ProcurementRequest request;

    @Column(name = "spare_part_id")
    private UUID sparePartId;

    @Column(name = "equipment_type_id")
    private UUID equipmentTypeId;

    @Column(name = "equipment_type_name")
    private String equipmentTypeName;

    @Column(name = "quantity", nullable = false)
    private double quantity;

    @Column(name = "received_quantity", nullable = false)
    private double receivedQuantity = 0;

    @Column(name = "remaining_quantity", nullable = false)
    private double remainingQuantity;

    @Column(name = "unit", nullable = false)
    private String unit;

    @Column(name = "unit_price")
    private Double unitPrice;

    @Column(name = "estimated_cost", nullable = false)
    private double estimatedCost = 0.0;

    @Column(name = "has_warranty", nullable = false)
    private Boolean hasWarranty = false;

    @Column(name = "warranty_start_date")
    private LocalDate warrantyStartDate;

    @Column(name = "warranty_end_date")
    private LocalDate warrantyEndDate;

    @Column(name = "warranty_duration_months")
    private Integer warrantyDurationMonths;

    @Column(name = "warranty_counteragent_id")
    private UUID warrantyCounteragentId;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;
}
