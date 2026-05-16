package com.toir.dto.equipment;

public record EquipmentStatsResponse(
        long totalInRegistry,
        long active,
        long inRepair,
        long decommissioned)
{}
