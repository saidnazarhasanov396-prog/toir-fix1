package com.toir.service;

import com.toir.dto.meter.MeterStatsResponse;
import com.toir.enums.MeterType;
import com.toir.repository.MeterReadingRepository;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.MeterStatsProjection;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeterServiceStatsTest {

    @Mock
    EquipmentMeterRepository meterRepository;
    @Mock
    MeterReadingRepository readingRepository;
    @Mock
    EquipmentRepository equipmentRepository;
    @Mock
    AuditBuilderService auditBuilderService;

    @InjectMocks
    MeterService service;

    @Test
    void getStatsWithNoFiltersReturnsMappedResponse() {
        MeterStatsProjection projection = mockProjection(20L, 15L, 200L, 3L);
        when(meterRepository.getMeterStats(isNull(), isNull(), isNull(), isNull())).thenReturn(projection);

        MeterStatsResponse stats = service.getStats(null, null, null, null);

        assertThat(stats.totalMeters()).isEqualTo(20);
        assertThat(stats.activeMeters()).isEqualTo(15);
        assertThat(stats.totalReadings()).isEqualTo(200);
        assertThat(stats.dueTriggers()).isEqualTo(3);
        verify(meterRepository).getMeterStats(null, null, null, null);
    }

    @Test
    void getStatsWithMeterTypePassesStringName() {
        MeterStatsProjection projection = mockProjection(5L, 5L, 50L, 0L);
        when(meterRepository.getMeterStats(isNull(), eq("ENGINE_HOURS"), isNull(), isNull())).thenReturn(projection);

        service.getStats(null, MeterType.ENGINE_HOURS, null, null);

        verify(meterRepository).getMeterStats(null, "ENGINE_HOURS", null, null);
    }

    @Test
    void getStatsWithEquipmentIdPassesIdToRepository() {
        UUID equipmentId = UUID.randomUUID();
        MeterStatsProjection projection = mockProjection(2L, 2L, 10L, 0L);
        when(meterRepository.getMeterStats(isNull(), isNull(), eq(equipmentId), isNull())).thenReturn(projection);

        service.getStats(null, null, equipmentId, null);

        verify(meterRepository).getMeterStats(null, null, equipmentId, null);
    }

    @Test
    void getStatsWithNullMeterTypePassesNullString() {
        MeterStatsProjection projection = mockProjection(10L, 10L, 0L, 0L);
        when(meterRepository.getMeterStats(isNull(), isNull(), isNull(), isNull())).thenReturn(projection);

        service.getStats(null, null, null, null);

        verify(meterRepository).getMeterStats(null, null, null, null);
    }

    @Test
    void getStatsHandlesNullProjectionValuesGracefully() {
        MeterStatsProjection projection = mockProjection(null, null, null, null);
        when(meterRepository.getMeterStats(any(), any(), any(), any())).thenReturn(projection);

        MeterStatsResponse stats = service.getStats(null, null, null, null);

        assertThat(stats.totalMeters()).isZero();
        assertThat(stats.activeMeters()).isZero();
        assertThat(stats.totalReadings()).isZero();
        assertThat(stats.dueTriggers()).isZero();
    }

    private MeterStatsProjection mockProjection(Long total, Long active, Long readings, Long due) {
        MeterStatsProjection p = mock(MeterStatsProjection.class);
        when(p.getTotalMeters()).thenReturn(total);
        when(p.getActiveMeters()).thenReturn(active);
        when(p.getTotalReadings()).thenReturn(readings);
        when(p.getDueTriggers()).thenReturn(due);
        return p;
    }
}
