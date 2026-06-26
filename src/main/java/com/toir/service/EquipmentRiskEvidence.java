package com.toir.service;

import com.toir.enums.CriticalityLevel;
import com.toir.enums.EquipmentStatus;

import java.util.UUID;

public record EquipmentRiskEvidence(
        UUID equipmentId,
        String criticalityCode,
        String criticalityName,
        CriticalityLevel criticalityLevel,
        Integer repairPriority,
        EquipmentStatus equipmentStatus,
        int safetyImpact,
        int productionImpact,
        int ecologicalImpact,
        int energyImpact,
        long openDefects,
        long recurringDefects,
        double mtbfHours,
        double mttrHours,
        double recentDowntimeHours,
        long overdueMaintenanceCount,
        long openHighRepairRequestCount
) {
}
