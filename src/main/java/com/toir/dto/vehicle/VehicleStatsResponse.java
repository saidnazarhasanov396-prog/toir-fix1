package com.toir.dto.vehicle;

public record VehicleStatsResponse(
        long total,
        long active,
        long inRepair,
        long outOfService
) {
}
