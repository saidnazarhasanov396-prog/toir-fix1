package com.toir.dto.criticalityclass;

import com.toir.entity.CriticalityLevel;
import com.toir.entity.CriticalityClass;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CriticalityClassDto(
        UUID id,
        @NotBlank String code,
        @NotBlank String name,
        String nameEn,
        String nameUz,
        @NotNull CriticalityLevel level,
        String description,
        Integer safetyImpact,
        Integer productionImpact,
        Integer ecologicalImpact,
        Integer energyImpact,
        String failureConsequence,
        Integer repairPriority
) {
    public static CriticalityClassDto from(CriticalityClass c) {
        return new CriticalityClassDto(
                c.getId(),
                c.getCode(),
                c.getName(),
                c.getNameEn(),
                c.getNameUz(),
                c.getLevel(),
                c.getDescription(),
                c.getSafetyImpact(),
                c.getProductionImpact(),
                c.getEcologicalImpact(),
                c.getEnergyImpact(),
                c.getFailureConsequence(),
                c.getRepairPriority()
        );
    }
}
