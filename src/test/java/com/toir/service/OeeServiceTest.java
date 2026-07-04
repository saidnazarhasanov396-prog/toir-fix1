package com.toir.service;

import com.toir.dto.oee.OeeFilter;
import com.toir.dto.oee.OeeSummary;
import com.toir.entity.OeeRecord;
import com.toir.repository.OeeRecordRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.service.oee.OeeMetricsCalculator;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OeeServiceTest {

    @Mock
    OeeRecordRepository repository;

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    @Spy
    OeeMetricsCalculator metricsCalculator = new OeeMetricsCalculator();

    @InjectMocks
    OeeService service;

    @Test
    void listShouldReturnAllActiveRecords() {
        UUID equipmentId = UUID.randomUUID();
        OeeRecord record = new OeeRecord();
        record.setId(UUID.randomUUID());
        record.setEquipmentId(equipmentId);
        record.setShiftStart(Instant.parse("2026-01-02T08:00:00Z"));
        record.setShiftEnd(Instant.parse("2026-01-02T16:00:00Z"));
        record.setPlannedProductionMinutes(480);
        record.setRunMinutes(420);
        record.setIdealCycleSeconds(30);
        record.setTotalCount(800);
        record.setGoodCount(780);
        record.setAvailability(0.875);
        record.setPerformance(0.952);
        record.setQuality(0.975);
        record.setOee(0.812);
        when(repository.search(null, null, null, null, null, null)).thenReturn(List.of(record));

        var result = service.list();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().equipmentId()).isEqualTo(equipmentId);
        verify(repository).search(null, null, null, null, null, null);
    }

    @Test
    void listRecalculatesMetricsInsteadOfReturningPersistedMetricColumns() {
        UUID equipmentId = UUID.randomUUID();
        OeeRecord record = new OeeRecord();
        record.setId(UUID.randomUUID());
        record.setEquipmentId(equipmentId);
        record.setShiftStart(Instant.parse("2026-06-23T09:00:00Z"));
        record.setShiftEnd(Instant.parse("2026-06-23T17:00:00Z"));
        record.setPlannedProductionMinutes(60);
        record.setRunMinutes(20);
        record.setIdealCycleSeconds(100);
        record.setTotalCount(20);
        record.setGoodCount(15);
        record.setAvailability(0.99);
        record.setPerformance(0.99);
        record.setQuality(0.99);
        record.setOee(0.99);

        OeeFilter filter = new OeeFilter(null, null, null, null, null, null, null, null, null, null, null, null, "shiftStart", "desc");
        when(repository.search(null, null, null, null, null, null)).thenReturn(List.of(record));

        var result = service.list(filter);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().availability()).isEqualTo(20.0 / 60.0);
        assertThat(result.getFirst().quality()).isEqualTo(15.0 / 20.0);
        assertThat(result.getFirst().oee()).isNotEqualTo(0.99);
    }

    @Test
    void listAppliesMetricRangeAfterBackendCalculation() {
        OeeRecord low = new OeeRecord();
        low.setId(UUID.randomUUID());
        low.setEquipmentId(UUID.randomUUID());
        low.setShiftStart(Instant.parse("2026-06-23T09:00:00Z"));
        low.setShiftEnd(Instant.parse("2026-06-23T17:00:00Z"));
        low.setPlannedProductionMinutes(100);
        low.setRunMinutes(50);
        low.setIdealCycleSeconds(30);
        low.setTotalCount(10);
        low.setGoodCount(10);

        OeeRecord high = new OeeRecord();
        high.setId(UUID.randomUUID());
        high.setEquipmentId(UUID.randomUUID());
        high.setShiftStart(Instant.parse("2026-06-24T09:00:00Z"));
        high.setShiftEnd(Instant.parse("2026-06-24T17:00:00Z"));
        high.setPlannedProductionMinutes(100);
        high.setRunMinutes(100);
        high.setIdealCycleSeconds(60);
        high.setTotalCount(100);
        high.setGoodCount(100);

        OeeFilter filter = new OeeFilter(null, null, null, null, null, null, 0.8, null, null, null, null, null, "shiftStart", "desc");
        when(repository.search(null, null, null, null, null, null)).thenReturn(List.of(low, high));

        var result = service.list(filter);

        assertThat(result).extracting("id").containsExactly(high.getId());
    }

    @Test
    void summaryWithBusinessSearchAndNoMatchingEquipmentShouldReturnZeroSummary() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-31T23:59:59Z");
        when(repository.search(null, "%compressor%", null, null, from, to)).thenReturn(List.of());

        OeeSummary summary = service.summary(null, "compressor", from, to);

        assertThat(summary.recordCount()).isZero();
        assertThat(summary.oee()).isZero();
        verify(repository).search(null, "%compressor%", null, null, from, to);
    }

    @Test
    void summaryWithBusinessSearchShouldAggregateMatchingEquipmentRecords() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-31T23:59:59Z");
        UUID equipmentId = UUID.randomUUID();

        OeeRecord record = new OeeRecord();
        record.setEquipmentId(equipmentId);
        record.setPlannedProductionMinutes(100);
        record.setRunMinutes(80);
        record.setIdealCycleSeconds(48);
        record.setTotalCount(100);
        record.setGoodCount(95);
        when(repository.search(null, "%eq-2026-1001%", null, null, from, to)).thenReturn(List.of(record));

        OeeSummary summary = service.summary(null, "EQ-2026-1001", from, to);

        assertThat(summary.equipmentId()).isNull();
        assertThat(summary.recordCount()).isEqualTo(1);
        assertThat(summary.availability()).isEqualTo(0.8);
        assertThat(summary.quality()).isEqualTo(0.95);
        verify(repository).search(null, "%eq-2026-1001%", null, null, from, to);
    }
}
