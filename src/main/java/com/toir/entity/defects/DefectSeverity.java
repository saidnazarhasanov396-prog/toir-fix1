package com.toir.entity.defects;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "defect_severities")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DefectSeverity extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "name_uz")
    private String nameUz;

    @Column(nullable = false)
    private int weight;

}
