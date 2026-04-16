package com.toir.defectcategory;

import com.toir.common.jpa.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "defect_categories")
public class DefectCategory extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "name_uz")
    private String nameUz;

    private String description;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNameEn() { return nameEn; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }
    public String getNameUz() { return nameUz; }
    public void setNameUz(String nameUz) { this.nameUz = nameUz; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
