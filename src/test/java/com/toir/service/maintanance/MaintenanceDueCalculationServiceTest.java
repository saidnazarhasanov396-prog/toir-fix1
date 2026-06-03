package com.toir.service.maintanance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.maintenanceplanning.MaintenanceDueCalculationDto;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.entity.maintenance.MaintenanceCompletionAnchor;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.MaintenanceRecalculationPolicy;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.enums.MeterType;
import com.toir.enums.PeriodicityUnit;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.maintenance.MaintenanceCompletionAnchorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceDueCalculationServiceTest {

    @Mock
    EquipmentMeterRepository meterRepository;

    @Mock
    MaintenanceCompletionAnchorRepository anchorRepository;

    MaintenanceDueCalculationService service;

    @BeforeEach
    void setUp() {
        service = new MaintenanceDueCalculationService(meterRepository, anchorRepository, new ObjectMapper());
    }

    @Test
    void meterTriggerWithoutCompletionHistoryDoesNotUseCalendarAnchor() {
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setTriggerMeterType(MeterType.ENGINE_HOURS);
        regulation.setTriggerMeterInterval(500.0);

        EquipmentMeter meter = new EquipmentMeter();
        meter.setId(UUID.randomUUID());
        meter.setEquipmentId(equipmentId);
        meter.setMeterType(MeterType.ENGINE_HOURS);
        meter.setCurrentValue(520.0);

        when(meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId)).thenReturn(java.util.List.of(meter));
        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.empty());

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.status()).isEqualTo(MaintenanceDueStatus.DUE);
        assertThat(result.dueByCalendar()).isFalse();
        assertThat(result.dueByMeter()).isTrue();
        assertThat(result.meterType()).isEqualTo(MeterType.ENGINE_HOURS);
        assertThat(result.meterCurrentValue()).isEqualTo(520.0);
        assertThat(result.meterInterval()).isEqualTo(500.0);
        assertThat(result.meterAnchorValue()).isZero();
        assertThat(result.meterRemaining()).isZero();
        assertThat(result.explanation()).doesNotContain("No completion anchor for calendar trigger");
    }

    @Test
    void returnsBlockedWhenConfiguredMeterIsMissing() {
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setTriggerMeterType(MeterType.ENGINE_HOURS);
        regulation.setTriggerMeterInterval(100.0);

        when(meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId)).thenReturn(java.util.List.of());
        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.empty());

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.status()).isEqualTo(MaintenanceDueStatus.BLOCKED);
        assertThat(result.explanation()).contains("meter");
    }

    @Test
    void calculatesDueFromLatestAnchorMeterSnapshot() {
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setTriggerMeterType(MeterType.ENGINE_HOURS);
        regulation.setTriggerMeterInterval(100.0);
        regulation.setTriggerPolicy(MaintenanceTriggerPolicy.ANY);

        EquipmentMeter meter = new EquipmentMeter();
        meter.setId(UUID.randomUUID());
        meter.setEquipmentId(equipmentId);
        meter.setMeterType(MeterType.ENGINE_HOURS);
        meter.setCurrentValue(251.0);

        MaintenanceCompletionAnchor anchor = new MaintenanceCompletionAnchor();
        anchor.setEquipmentId(equipmentId);
        anchor.setRegulationId(regulation.getId());
        anchor.setPerformedAt(Instant.now().minusSeconds(3600));
        anchor.setRecalculationPolicy(MaintenanceRecalculationPolicy.FROM_ACTUAL_COMPLETION);
        anchor.setMeterSnapshots("""
                [{"meterType":"ENGINE_HOURS","value":150.0}]
                """);

        when(meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId)).thenReturn(java.util.List.of(meter));
        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.of(anchor));

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.status()).isEqualTo(MaintenanceDueStatus.DUE);
        assertThat(result.meterRemaining()).isZero();
        assertThat(result.meterCurrentValue()).isEqualTo(251.0);
    }

    @Test
    void manualPolicyIsNotDue() {
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setTriggerPolicy(MaintenanceTriggerPolicy.MANUAL);

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.status()).isEqualTo(MaintenanceDueStatus.NOT_DUE);
        assertThat(result.explanation()).contains("Manual");
    }

    private MaintenanceRegulation regulation(UUID equipmentId) {
        MaintenanceRegulation regulation = new MaintenanceRegulation();
        regulation.setId(UUID.randomUUID());
        regulation.setEquipmentTypeId(UUID.randomUUID());
        regulation.setPeriodicityUnit(PeriodicityUnit.MONTH);
        regulation.setPeriodicityValue(1);
        regulation.setTriggerPolicy(MaintenanceTriggerPolicy.ANY);
        return regulation;
    }
}
