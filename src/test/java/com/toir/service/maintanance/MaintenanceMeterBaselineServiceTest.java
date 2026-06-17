package com.toir.service.maintanance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.entity.maintenance.MaintenanceCompletionAnchor;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.MaintenanceRecalculationPolicy;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.enums.MeterType;
import com.toir.enums.PeriodicityUnit;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.MaintenanceCompletionAnchorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceMeterBaselineServiceTest {

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    EquipmentMeterRepository equipmentMeterRepository;

    @Mock
    MaintenanceCompletionAnchorRepository anchorRepository;

    @Mock
    EquipmentMaintenanceEffectiveRuleResolver effectiveRuleResolver;

    ObjectMapper objectMapper = new ObjectMapper();

    MaintenanceMeterBaselineService service;

    @BeforeEach
    void setUp() {
        service = new MaintenanceMeterBaselineService(
                equipmentRepository,
                equipmentMeterRepository,
                anchorRepository,
                effectiveRuleResolver,
                objectMapper
        );
        ReflectionTestUtils.setField(service, "clock",
                Clock.fixed(Instant.parse("2026-06-17T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void seedForRegulationCreatesInitialMeterBaselineFromCurrentMeterValue() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentTypeId, MeterType.MILEAGE_KM, 15_000.0);
        Equipment equipment = equipment(equipmentId, equipmentTypeId);
        EquipmentMeter meter = meter(equipmentId, MeterType.MILEAGE_KM, 4_000.0);

        when(equipmentRepository.findAllForMaintenanceRegulations(equipmentTypeId)).thenReturn(List.of(equipment));
        when(effectiveRuleResolver.resolveApplicable(equipmentId))
                .thenReturn(List.of(EquipmentMaintenanceEffectiveRule.fromRegulation(equipmentId, regulation)));
        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.empty());
        when(equipmentMeterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId))
                .thenReturn(List.of(meter));

        service.seedForRegulation(regulation);

        ArgumentCaptor<MaintenanceCompletionAnchor> anchorCaptor =
                ArgumentCaptor.forClass(MaintenanceCompletionAnchor.class);
        verify(anchorRepository).save(anchorCaptor.capture());
        MaintenanceCompletionAnchor anchor = anchorCaptor.getValue();
        assertThat(anchor.getEquipmentId()).isEqualTo(equipmentId);
        assertThat(anchor.getRegulationId()).isEqualTo(regulation.getId());
        assertThat(anchor.getEquipmentMaintenanceRuleId()).isNull();
        assertThat(anchor.getPerformedAt()).isEqualTo(Instant.parse("2026-06-17T00:00:00Z"));
        assertThat(anchor.getRecalculationPolicy()).isEqualTo(MaintenanceRecalculationPolicy.FROM_ACTUAL_COMPLETION);
        assertThat(anchor.getSource()).isEqualTo("INITIAL_METER_BASELINE");

        JsonNode snapshot = objectMapper.readTree(anchor.getMeterSnapshots()).get(0);
        assertThat(snapshot.path("meterId").asText()).isEqualTo(meter.getId().toString());
        assertThat(snapshot.path("meterType").asText()).isEqualTo("MILEAGE_KM");
        assertThat(snapshot.path("value").asDouble()).isEqualTo(4_000.0);
        assertThat(snapshot.path("readAt").asText()).isEqualTo("2026-06-16T12:00:00Z");
    }

    @Test
    void seedForRegulationDoesNotOverwriteExistingAnchor() {
        UUID equipmentId = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentTypeId, MeterType.MILEAGE_KM, 15_000.0);
        Equipment equipment = equipment(equipmentId, equipmentTypeId);
        MaintenanceCompletionAnchor existing = new MaintenanceCompletionAnchor();

        when(equipmentRepository.findAllForMaintenanceRegulations(equipmentTypeId)).thenReturn(List.of(equipment));
        when(effectiveRuleResolver.resolveApplicable(equipmentId))
                .thenReturn(List.of(EquipmentMaintenanceEffectiveRule.fromRegulation(equipmentId, regulation)));
        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.of(existing));

        service.seedForRegulation(regulation);

        verify(anchorRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(equipmentMeterRepository, never()).findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId);
    }

    private MaintenanceRegulation regulation(UUID equipmentTypeId, MeterType meterType, double interval) {
        MaintenanceRegulation regulation = new MaintenanceRegulation();
        regulation.setId(UUID.randomUUID());
        regulation.setEquipmentTypeId(equipmentTypeId);
        regulation.setCode("MR-2026-0001");
        regulation.setName("Oil change");
        regulation.setMaintenanceKind(MaintenanceKind.PREVENTIVE);
        regulation.setNormativeLaborHours(2.0);
        regulation.setActive(true);
        regulation.setPeriodicityUnit(PeriodicityUnit.MONTH);
        regulation.setPeriodicityValue(1);
        regulation.setTriggerMeterType(meterType);
        regulation.setTriggerMeterInterval(interval);
        regulation.setTriggerPolicy(MaintenanceTriggerPolicy.ANY);
        regulation.setRecalculationPolicy(MaintenanceRecalculationPolicy.FROM_ACTUAL_COMPLETION);
        regulation.setCreatedAt(Instant.parse("2026-06-17T00:00:00Z"));
        return regulation;
    }

    private Equipment equipment(UUID equipmentId, UUID equipmentTypeId) {
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setEquipmentTypeId(equipmentTypeId);
        equipment.setName("Truck");
        return equipment;
    }

    private EquipmentMeter meter(UUID equipmentId, MeterType meterType, double currentValue) {
        EquipmentMeter meter = new EquipmentMeter();
        meter.setId(UUID.randomUUID());
        meter.setEquipmentId(equipmentId);
        meter.setMeterType(meterType);
        meter.setName("Odometer");
        meter.setUnit("km");
        meter.setCurrentValue(currentValue);
        meter.setLastReadAt(Instant.parse("2026-06-16T12:00:00Z"));
        meter.setActive(true);
        return meter;
    }
}
