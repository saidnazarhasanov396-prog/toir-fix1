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

@Entity
@Table(name = "equipment_attribute_option_sources")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EquipmentAttributeOptionSource extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "name_ru")
    private String nameRu;

    @Column(name = "name_uz")
    private String nameUz;

    private String description;
}
