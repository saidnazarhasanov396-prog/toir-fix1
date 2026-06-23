package com.toir.service;

import com.toir.entity.ReliabilityMetric;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IndustrialKpiAggregationsTest {

    @Test
    void latestReliabilityMetricsKeepsOnlyNewestMetricPerEquipment() {
        UUID equipmentA = UUID.randomUUID();
        UUID equipmentB = UUID.randomUUID();
        ReliabilityMetric oldA = metric(equipmentA, "2026-01-01", 100, 10);
        ReliabilityMetric latestA = metric(equipmentA, "2026-02-01", 200, 20);
        ReliabilityMetric onlyB = metric(equipmentB, "2026-01-15", 300, 30);

        List<ReliabilityMetric> result = IndustrialKpiAggregations.latestReliabilityMetrics(
                List.of(latestA, oldA, onlyB)
        );

        assertThat(result).containsExactlyInAnyOrder(latestA, onlyB);
    }

    private ReliabilityMetric metric(UUID equipmentId, String date, double mtbf, double mttr) {
        return ReliabilityMetric.builder()
                .equipmentId(equipmentId)
                .metricDate(LocalDate.parse(date))
                .mtbfHours(mtbf)
                .mttrHours(mttr)
                .build();
    }
}
