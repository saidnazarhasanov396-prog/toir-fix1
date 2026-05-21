package com.toir.entity.equipment;

import com.toir.dto.equipmentattribute.EquipmentAttributeOptionDto;
import com.toir.entity.BaseEntity;
import com.toir.enums.EquipmentAttributeDataType;
import com.toir.persistence.EquipmentAttributeOptionListJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "equipment_attribute_definitions")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EquipmentAttributeDefinition extends BaseEntity {

    @Column(name = "equipment_type_id", nullable = false)
    private UUID equipmentTypeId;

    @Column(name = "attribute_key", nullable = false)
    private String key;

    @Column(nullable = false)
    private String label;

    @Column(name = "label_ru")
    private String labelRu;

    @Column(name = "label_uz")
    private String labelUz;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false)
    private EquipmentAttributeDataType dataType;

    private String unit;

    @Column(name = "is_required", nullable = false)
    private boolean required;

    @Column(name = "min_value")
    private Double minValue;

    @Column(name = "max_value")
    private Double maxValue;

    @Column(name = "option_source_id")
    private UUID optionSourceId;

    @Convert(converter = EquipmentAttributeOptionListJsonConverter.class)
    @Column(name = "options_json", columnDefinition = "text")
    private List<EquipmentAttributeOptionDto> options;

    @Column(name = "group_name")
    private String groupName;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
