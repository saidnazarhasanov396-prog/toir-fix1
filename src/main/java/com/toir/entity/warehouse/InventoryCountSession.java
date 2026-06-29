package com.toir.entity.warehouse;

import com.toir.entity.BaseEntity;
import com.toir.enums.InventoryCountScopeType;
import com.toir.enums.InventoryCountSessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_count_sessions")
@Getter
@Setter
public class InventoryCountSession extends BaseEntity {

    @Column(name = "session_number", nullable = false, length = 64)
    private String sessionNumber;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InventoryCountSessionStatus status = InventoryCountSessionStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 32)
    private InventoryCountScopeType scopeType = InventoryCountScopeType.WAREHOUSE;

    @Column(name = "scope_zone", length = 64)
    private String scopeZone;

    @Column(name = "scope_bin_id")
    private UUID scopeBinId;

    @Column(name = "scope_spare_part_id")
    private UUID scopeSparePartId;

    @Column(name = "scope_abc_class", length = 1)
    private String scopeAbcClass;

    @Column(name = "random_sample_size")
    private Integer randomSampleSize;

    @Column(name = "blind_count", nullable = false)
    private boolean blindCount;

    @Column(name = "created_by_id")
    private UUID createdById;

    @Column(name = "approved_by_id")
    private UUID approvedById;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "document_number", length = 100)
    private String documentNumber;

    @Column(columnDefinition = "text")
    private String comment;

    @PrePersist
    void prepareForInsert() {
        if (status == null) {
            status = InventoryCountSessionStatus.DRAFT;
        }
        if (scopeType == null) {
            scopeType = InventoryCountScopeType.WAREHOUSE;
        }
    }
}
