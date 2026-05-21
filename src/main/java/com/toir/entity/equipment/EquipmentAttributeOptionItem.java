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

import java.util.UUID;

@Entity
@Table(name = "equipment_attribute_option_items")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EquipmentAttributeOptionItem extends BaseEntity {

    @Column(name = "option_source_id", nullable = false)
    private UUID optionSourceId;

    @Column(name = "option_id", nullable = false)
    private String optionId;

    @Column(nullable = false)
    private String label;

    @Column(name = "label_ru")
    private String labelRu;

    @Column(name = "label_uz")
    private String labelUz;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(nullable = false)
    private boolean active = true;
}
