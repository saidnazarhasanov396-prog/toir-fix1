package com.toir.entity;

import com.toir.enums.DepartmentType;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "departments")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
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

}
