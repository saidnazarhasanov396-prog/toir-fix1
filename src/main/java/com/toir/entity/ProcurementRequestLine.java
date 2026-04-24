package com.toir.entity;

import jakarta.persistence.*;
import lombok.*;

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

    @Column(name = "spare_part_id", nullable = false)
    private UUID sparePartId;

    @Column(name = "quantity", nullable = false)
    private double quantity;

    @Column(name = "unit", nullable = false)
    private String unit;

    @Column(name = "unit_price")
    private Double unitPrice;

    @Column(name = "estimated_cost", nullable = false)
    private double estimatedCost = 0.0;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;
}
