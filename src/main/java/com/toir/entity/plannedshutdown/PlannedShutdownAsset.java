package com.toir.entity.plannedshutdown;

import com.toir.entity.BaseEntity;
import com.toir.enums.PlannedShutdownAssetDisposition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "planned_shutdown_assets")
@Getter
@Setter
@NoArgsConstructor
public class PlannedShutdownAsset extends BaseEntity {
    @Column(name = "planned_shutdown_id", nullable = false)
    private UUID plannedShutdownId;

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "disposition", nullable = false, length = 32)
    private PlannedShutdownAssetDisposition disposition;

    @Column(name = "inclusion_reason", columnDefinition = "text")
    private String inclusionReason;

    @Column(name = "order_number", nullable = false)
    private Integer orderNumber = 0;
}
