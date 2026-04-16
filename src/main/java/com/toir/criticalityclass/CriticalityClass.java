package com.toir.criticalityclass;

import com.toir.common.enums.CriticalityLevel;
import com.toir.common.jpa.BaseEntity;
import jakarta.persistence.*;

/**
 * Класс критичности оборудования. По ТЗ §4.2.7 учитывает влияние на
 * безопасность, производство, экологию и энергопотребление, а также
 * последствия отказа и приоритет ремонта.
 */
@Entity
@Table(name = "criticality_classes")
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

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNameEn() { return nameEn; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }
    public String getNameUz() { return nameUz; }
    public void setNameUz(String nameUz) { this.nameUz = nameUz; }
    public CriticalityLevel getLevel() { return level; }
    public void setLevel(CriticalityLevel level) { this.level = level; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getSafetyImpact() { return safetyImpact; }
    public void setSafetyImpact(Integer safetyImpact) { this.safetyImpact = safetyImpact; }
    public Integer getProductionImpact() { return productionImpact; }
    public void setProductionImpact(Integer productionImpact) { this.productionImpact = productionImpact; }
    public Integer getEcologicalImpact() { return ecologicalImpact; }
    public void setEcologicalImpact(Integer ecologicalImpact) { this.ecologicalImpact = ecologicalImpact; }
    public Integer getEnergyImpact() { return energyImpact; }
    public void setEnergyImpact(Integer energyImpact) { this.energyImpact = energyImpact; }
    public String getFailureConsequence() { return failureConsequence; }
    public void setFailureConsequence(String failureConsequence) { this.failureConsequence = failureConsequence; }
    public Integer getRepairPriority() { return repairPriority; }
    public void setRepairPriority(Integer repairPriority) { this.repairPriority = repairPriority; }
}
