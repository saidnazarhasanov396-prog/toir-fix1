package com.toir.entity.equipment;

import com.toir.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "equipment_attribute_values")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EquipmentAttributeValue extends BaseEntity {

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "attribute_definition_id", nullable = false)
    private UUID attributeDefinitionId;

    @Column(name = "value_text", columnDefinition = "text")
    private String valueText;

    @Column(name = "value_number")
    private Double valueNumber;

    @Column(name = "value_date")
    private LocalDate valueDate;

    @Column(name = "value_boolean")
    private Boolean valueBoolean;

    @Column(name = "value_option")
    private String valueOption;

    @Column(name = "value_json", columnDefinition = "text")
    private String valueJson;
}
