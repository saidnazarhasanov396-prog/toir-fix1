package com.toir.entity;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "certification_types")
public class CertificationType extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "name_uz")
    private String nameUz;

    /** Длительность сертификации в месяцах (для авто-расчёта expiresAt). */
    @Column(name = "validity_months")
    private Integer validityMonths;

    /** Категория: SAFETY / PROFESSIONAL / ELECTRICAL / WELDING / HEIGHT / NDT. */
    @Column(nullable = false)
    private String category = "PROFESSIONAL";

    @Column(columnDefinition = "text")
    private String description;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNameEn() { return nameEn; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }
    public String getNameUz() { return nameUz; }
    public void setNameUz(String nameUz) { this.nameUz = nameUz; }
    public Integer getValidityMonths() { return validityMonths; }
    public void setValidityMonths(Integer validityMonths) { this.validityMonths = validityMonths; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
