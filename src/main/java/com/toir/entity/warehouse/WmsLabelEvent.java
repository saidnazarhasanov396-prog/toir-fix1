package com.toir.entity.warehouse;

import com.toir.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "wms_label_events")
@Getter
@Setter
public class WmsLabelEvent extends BaseEntity {

    @Column(name = "label_type", nullable = false, length = 32)
    private String labelType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "target_code", length = 128)
    private String targetCode;

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Column(name = "bin_id")
    private UUID binId;

    @Column(name = "spare_part_id")
    private UUID sparePartId;

    @Column(name = "equipment_id")
    private UUID equipmentId;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;
}
