package com.toir.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "failure_reasons")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FailureReason extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "name_uz")
    private String nameUz;

    private String description;
}
