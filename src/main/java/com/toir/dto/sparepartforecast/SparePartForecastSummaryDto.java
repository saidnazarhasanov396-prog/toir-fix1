package com.toir.dto.sparepartforecast;

import java.time.Instant;
import java.util.List;

public record SparePartForecastSummaryDto(
        Instant periodStart,
        Instant periodEnd,
        List<SparePartForecastItemDto> items
) {
}
