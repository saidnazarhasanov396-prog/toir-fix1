package com.toir.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "certification_types")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
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
}
