package com.toir.entity.warehouse;

import com.toir.entity.BaseEntity;
import com.toir.enums.WarehouseStockStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "warehouse_stock_balances")
@Getter
@Setter
public class WarehouseStockBalance extends BaseEntity {

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "spare_part_id", nullable = false)
    private UUID sparePartId;

    @Column(name = "bin_id")
    private UUID binId;

    @Column(name = "lot_number", length = 100)
    private String lotNumber;

    @Column(name = "serial_number", length = 128)
    private String serialNumber;

    @Column(name = "identity_key", nullable = false, length = 512)
    private String identityKey;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "stock_status", nullable = false, length = 32)
    private WarehouseStockStatus stockStatus = WarehouseStockStatus.AVAILABLE;

    @Column(name = "quality_hold_reason")
    private String qualityHoldReason;

    @Column(name = "quality_checked_at")
    private Instant qualityCheckedAt;

    @Column(name = "quality_checked_by_id")
    private UUID qualityCheckedById;

    @Column(name = "qty_on_hand", nullable = false, precision = 19, scale = 4)
    private BigDecimal qtyOnHand = BigDecimal.ZERO;

    @Column(name = "qty_reserved", nullable = false, precision = 19, scale = 4)
    private BigDecimal qtyReserved = BigDecimal.ZERO;

    @Column(name = "avg_cost", precision = 19, scale = 4)
    private BigDecimal avgCost;

    @Version
    private Long version;

    public BigDecimal getAvailableQty() {
        if (stockStatus != WarehouseStockStatus.AVAILABLE) {
            return BigDecimal.ZERO;
        }
        return zero(qtyOnHand).subtract(zero(qtyReserved));
    }

    @PrePersist
    @PreUpdate
    public void prepareForSave() {
        qtyOnHand = zero(qtyOnHand);
        qtyReserved = zero(qtyReserved);
        if (qtyOnHand.signum() < 0) {
            throw new IllegalStateException("qtyOnHand cannot be negative");
        }
        if (qtyReserved.signum() < 0) {
            throw new IllegalStateException("qtyReserved cannot be negative");
        }
        if (qtyReserved.compareTo(qtyOnHand) > 0) {
            throw new IllegalStateException("qtyReserved cannot exceed qtyOnHand");
        }
        if (stockStatus == null) {
            stockStatus = WarehouseStockStatus.AVAILABLE;
        }
        identityKey = buildIdentityKey(warehouseId, sparePartId, binId, lotNumber, serialNumber, expiryDate, stockStatus);
    }

    public static String buildIdentityKey(UUID warehouseId,
                                          UUID sparePartId,
                                          UUID binId,
                                          String lotNumber,
                                          String serialNumber) {
        return buildIdentityKey(
                warehouseId,
                sparePartId,
                binId,
                lotNumber,
                serialNumber,
                null,
                WarehouseStockStatus.AVAILABLE
        );
    }

    public static String buildIdentityKey(UUID warehouseId,
                                          UUID sparePartId,
                                          UUID binId,
                                          String lotNumber,
                                          String serialNumber,
                                          LocalDate expiryDate,
                                          WarehouseStockStatus stockStatus) {
        return idOf(warehouseId) + "|" +
                idOf(sparePartId) + "|" +
                idOf(binId) + "|" +
                normalize(lotNumber) + "|" +
                normalize(serialNumber) + "|" +
                (expiryDate == null ? "" : expiryDate) + "|" +
                (stockStatus == null ? WarehouseStockStatus.AVAILABLE : stockStatus).name();
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String idOf(UUID id) {
        return id == null ? "0" : id.toString();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
