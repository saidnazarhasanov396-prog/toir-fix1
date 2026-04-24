package com.toir.entity;

import com.toir.enums.DepartmentType;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "departments")
public class Department extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "name_uz")
    private String nameUz;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DepartmentType type;

    @Column(name = "parent_id")
    private UUID parentId;

    private String description;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNameEn() { return nameEn; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }
    public String getNameUz() { return nameUz; }
    public void setNameUz(String nameUz) { this.nameUz = nameUz; }
    public DepartmentType getType() { return type; }
    public void setType(DepartmentType type) { this.type = type; }
    public UUID getParentId() { return parentId; }
    public void setParentId(UUID parentId) { this.parentId = parentId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
