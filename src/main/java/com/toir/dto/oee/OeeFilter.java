package com.toir.dto.oee;

import java.time.Instant;
import java.util.UUID;

public record OeeFilter(
        UUID equipmentId,
        String equipmentSearch,
        UUID departmentId,
        UUID equipmentTypeId,
        Instant from,
        Instant to,
        Double minOee,
        Double maxOee,
        Double minAvailability,
        Double maxAvailability,
        Double minQuality,
        Double maxQuality,
        String sortBy,
        String sortDir
) {
}
