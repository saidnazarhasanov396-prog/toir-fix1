package com.toir.entity.equipment;

import com.toir.entity.BaseEntity;
import com.toir.enums.CriticalityLevel;
import jakarta.persistence.*;
import lombok.*;

/**
 * Класс критичности оборудования. По ТЗ §4.2.7 учитывает влияние на
 * безопасность, производство, экологию и энергопотребление, а также
 * последствия отказа и приоритет ремонта.
 */
@Entity
@Table(name = "criticality_classes")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CriticalityClass extends BaseEntity {

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
    private CriticalityLevel level;

    private String description;

    /** Влияние на безопасность (0..5). */
    @Column(name = "safety_impact")
    private Integer safetyImpact;

    /** Влияние на производство / выпуск продукции (0..5). */
    @Column(name = "production_impact")
    private Integer productionImpact;

    /** Экологическое влияние (0..5). */
    @Column(name = "ecological_impact")
    private Integer ecologicalImpact;

    /** Влияние на энергопотребление (0..5). */
    @Column(name = "energy_impact")
    private Integer energyImpact;

    /** Последствия отказа (текст: остановка линии, утечка, авария...). */
    @Column(name = "failure_consequence", columnDefinition = "text")
    private String failureConsequence;

    /** Приоритет ремонта 1..5 (1 = наивысший). */
    @Column(name = "repair_priority")
    private Integer repairPriority;

}
