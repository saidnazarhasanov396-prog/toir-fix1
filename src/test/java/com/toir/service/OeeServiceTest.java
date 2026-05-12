package com.toir.service;

import com.toir.dto.oee.OeeSummary;
import com.toir.entity.OeeRecord;
import com.toir.repository.OeeRecordRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OeeServiceTest {

    @Mock
    OeeRecordRepository repository;

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    @InjectMocks
    OeeService service;

    @Test
    void summaryWithBusinessSearchAndNoMatchingEquipmentShouldReturnZeroSummary() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-31T23:59:59Z");
        when(equipmentRepository.findIdsByBusinessSearch("%compressor%")).thenReturn(List.of());

        OeeSummary summary = service.summary(null, "compressor", from, to);

        assertThat(summary.recordCount()).isZero();
        assertThat(summary.oee()).isZero();
        verify(equipmentRepository).findIdsByBusinessSearch("%compressor%");
        verifyNoInteractions(repository);
    }

    @Test
    void summaryWithBusinessSearchShouldAggregateMatchingEquipmentRecords() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-31T23:59:59Z");
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findIdsByBusinessSearch("%eq-2026-1001%")).thenReturn(List.of(equipmentId));

        OeeRecord record = new OeeRecord();
        record.setEquipmentId(equipmentId);
        record.setPlannedProductionMinutes(100);
        record.setRunMinutes(80);
        record.setIdealCycleSeconds(48);
        record.setTotalCount(100);
        record.setGoodCount(95);
        when(repository.findAllByEquipmentIdInAndShiftStartBetweenAndIsDeletedFalseOrderByShiftStartAsc(List.of(equipmentId), from, to))
                .thenReturn(List.of(record));

        OeeSummary summary = service.summary(null, "EQ-2026-1001", from, to);

        assertThat(summary.equipmentId()).isEqualTo(equipmentId);
        assertThat(summary.recordCount()).isEqualTo(1);
        assertThat(summary.availability()).isEqualTo(0.8);
        assertThat(summary.quality()).isEqualTo(0.95);
        verify(equipmentRepository).findIdsByBusinessSearch("%eq-2026-1001%");
        verify(repository).findAllByEquipmentIdInAndShiftStartBetweenAndIsDeletedFalseOrderByShiftStartAsc(List.of(equipmentId), from, to);
    }
}
