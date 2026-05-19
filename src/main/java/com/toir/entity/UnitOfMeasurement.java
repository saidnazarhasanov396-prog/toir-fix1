package com.toir.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "units_of_measurement")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UnitOfMeasurement extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "name_uz")
    private String nameUz;

}
