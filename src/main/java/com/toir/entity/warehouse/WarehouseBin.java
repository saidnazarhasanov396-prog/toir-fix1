package com.toir.entity.warehouse;

import com.toir.entity.BaseEntity;
import com.toir.enums.WarehouseBinType;
import com.toir.enums.WarehouseQualityZoneType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "warehouse_bins",
        uniqueConstraints = @UniqueConstraint(columnNames = {"warehouse_id", "code"}))
@Getter
@Setter
public class WarehouseBin extends BaseEntity {

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(nullable = false, length = 100)
    private String code;

    private String zone;
    private String aisle;
    private String rack;

    @Column(name = "shelf_level")
    private String shelfLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "bin_type", length = 64)
    private WarehouseBinType binType;

    @Enumerated(EnumType.STRING)
    @Column(name = "quality_zone_type", nullable = false, length = 32)
    private WarehouseQualityZoneType qualityZoneType = WarehouseQualityZoneType.STORAGE;

    @Column(name = "temperature_zone", length = 64)
    private String temperatureZone;

    @Column(name = "hazard_class", length = 64)
    private String hazardClass;

    @Column(name = "allow_mixed_spare_parts", nullable = false)
    private boolean allowMixedSpareParts = true;

    @Column(name = "allow_mixed_lots", nullable = false)
    private boolean allowMixedLots = true;

    @Column(length = 128)
    private String barcode;

    @Column(name = "qr_payload", columnDefinition = "text")
    private String qrPayload;

    @Column(name = "max_weight_kg", precision = 19, scale = 4)
    private BigDecimal maxWeightKg;

    @Column(name = "max_volume_m3", precision = 19, scale = 4)
    private BigDecimal maxVolumeM3;

    @Column(name = "coord_x", precision = 19, scale = 4)
    private BigDecimal coordX;

    @Column(name = "coord_y", precision = 19, scale = 4)
    private BigDecimal coordY;

    @Column(precision = 19, scale = 4)
    private BigDecimal width;

    @Column(precision = 19, scale = 4)
    private BigDecimal height;

    @Column(nullable = false)
    private boolean blocked;

    @Column(name = "block_reason")
    private String blockReason;

    @Column(name = "blocked_at")
    private Instant blockedAt;

    @Column(nullable = false)
    private boolean frozen;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "travel_sequence")
    private Integer travelSequence;

    @Column(name = "bin_level")
    private Integer binLevel;
}
