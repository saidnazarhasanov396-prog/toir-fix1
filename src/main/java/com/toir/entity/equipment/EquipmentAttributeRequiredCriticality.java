package com.toir.entity.equipment;

import com.toir.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "equipment_attribute_required_criticality")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EquipmentAttributeRequiredCriticality extends BaseEntity {

    @Column(name = "attribute_definition_id", nullable = false)
    private UUID attributeDefinitionId;

    @Column(name = "criticality_class_id", nullable = false)
    private UUID criticalityClassId;
}
