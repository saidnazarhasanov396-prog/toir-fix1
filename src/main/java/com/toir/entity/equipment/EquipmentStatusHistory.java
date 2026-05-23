package com.toir.entity.equipment;

import com.toir.entity.BaseEntity;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.EquipmentStatusSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "equipment_status_history")
@Getter
@Setter
public class EquipmentStatusHistory extends BaseEntity {

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false)
    private EquipmentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private EquipmentStatus toStatus;

    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private EquipmentStatusSource source;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "related_entity_type", length = 100)
    private String relatedEntityType;

    @Column(name = "related_entity_id")
    private UUID relatedEntityId;

    @Column(name = "note", length = 1000)
    private String note;
}
