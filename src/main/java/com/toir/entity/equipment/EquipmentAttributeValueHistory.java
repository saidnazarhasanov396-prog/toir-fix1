package com.toir.entity.equipment;

import com.toir.entity.BaseEntity;
import com.toir.enums.EquipmentAttributeValueHistorySource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "equipment_attribute_value_history")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EquipmentAttributeValueHistory extends BaseEntity {

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "attribute_definition_id", nullable = false)
    private UUID attributeDefinitionId;

    @Column(name = "attribute_key", nullable = false)
    private String attributeKey;

    @Column(name = "attribute_label", nullable = false)
    private String attributeLabel;

    @Column(name = "old_value", columnDefinition = "text")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "text")
    private String newValue;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EquipmentAttributeValueHistorySource source;

    @Column(columnDefinition = "text")
    private String reason;
}
