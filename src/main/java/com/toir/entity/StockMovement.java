package com.toir.entity;
import com.toir.enums.StockMovementType;
import com.toir.enums.StockMovementSourceType;
import com.toir.enums.WarehouseStockStatus;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "stock_movements")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Deprecated(forRemoval = false)
public class StockMovement extends ActorStampedEntity {

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "spare_part_id")
    private UUID sparePartId;

    @Column(name = "equipment_type_id")
    private UUID equipmentTypeId;

    @Column(name = "work_order_id")
    private UUID workOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockMovementType type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit")
    private String unit;

    @Column(name = "unit_cost")
    private Double unitCost;

    @Column(name = "unit_price", precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_amount", precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "document_number")
    private String documentNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 64)
    private StockMovementSourceType sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "source_line_id")
    private UUID sourceLineId;

    @Column(name = "responsible_person_id")
    private UUID responsiblePersonId;

    @Column(name = "taken_by_id")
    private UUID takenById;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "supplier_name")
    private String supplierName;

    @Column(name = "movement_date")
    private LocalDate movementDate;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "comment", columnDefinition = "text")
    private String comment;

    @Column(name = "bin_id")
    private UUID binId;

    @Column(name = "from_bin_id")
    private UUID fromBinId;

    @Column(name = "to_bin_id")
    private UUID toBinId;

    @Column(name = "source_bin_id")
    private UUID sourceBinId;

    @Column(name = "destination_bin_id")
    private UUID destinationBinId;

    @Column(name = "lot_number", length = 100)
    private String lotNumber;

    @Column(name = "serial_number", length = 128)
    private String serialNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "stock_status", nullable = false, length = 32)
    private WarehouseStockStatus stockStatus = WarehouseStockStatus.AVAILABLE;

    @Column(name = "source_document_no", length = 100)
    private String sourceDocumentNo;

    @Column(name = "source_document_date")
    private LocalDate sourceDocumentDate;
}
