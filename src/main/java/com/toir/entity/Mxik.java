package com.toir.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "mxik")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Mxik extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 20)
    private String kod;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(name = "group_name")
    private String groupName;

    @Column(name = "position_name")
    private String positionName;

    @Column(name = "name_uz_latn")
    private String nameUzLatn;

    @Column(name = "name_ru")
    private String nameRu;

    @Column(name = "group_name_ru")
    private String groupNameRu;

    @Column(name = "group_name_cyril")
    private String groupNameCyril;

    @Column(name = "class_name")
    private String className;

    @Column(name = "class_name_ru")
    private String classNameRu;

    @Column(name = "class_name_cyril")
    private String classNameCyril;

    @Column(name = "position_name_ru")
    private String positionNameRu;

    @Column(name = "position_name_cyril")
    private String positionNameCyril;

    @Column(name = "sub_position_name")
    private String subPositionName;

    @Column(name = "sub_position_name_ru")
    private String subPositionNameRu;

    @Column(name = "sub_position_name_cyril")
    private String subPositionNameCyril;

    @Column(name = "brand_name")
    private String brandName;

    @Column(name = "brand_name_ru")
    private String brandNameRu;

    @Column(name = "brand_name_cyril")
    private String brandNameCyril;

    @Column(name = "attribute_name")
    private String attributeName;

    @Column(name = "attribute_name_ru")
    private String attributeNameRu;

    @Column(name = "attribute_name_cyril")
    private String attributeNameCyril;

    @Column(name = "barcode")
    private String barcode;
}
