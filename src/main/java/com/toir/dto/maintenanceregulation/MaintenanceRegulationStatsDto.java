package com.toir.dto.maintenanceregulation;

public record MaintenanceRegulationStatsDto(
        long total,
        long active,
        long preventive,
        long overhaul
) {}
