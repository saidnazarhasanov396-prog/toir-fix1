package com.toir.service;

import com.toir.dto.maintenanceplanning.MaintenanceDueCalculationDto;
import com.toir.dto.meter.MeterTriggerMatch;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.enums.MeterType;
import com.toir.enums.PeriodicityUnit;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.service.maintanance.EquipmentMaintenanceEffectiveRule;
import com.toir.service.maintanance.EquipmentMaintenanceEffectiveRuleResolver;
import com.toir.service.maintanance.MaintenanceDueCalculationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeterTriggerServiceTest {

    @Mock
    EquipmentMeterRepository meterRepository;

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    EquipmentMaintenanceEffectiveRuleResolver effectiveRuleResolver;

    @Mock
    MaintenanceDueCalculationService dueCalculationService;

    MeterTriggerService service;

    @BeforeEach
    void setUp() {
        service = new MeterTriggerService(
                meterRepository,
                equipmentRepository,
                effectiveRuleResolver,
                dueCalculationService
        );
    }

    @Test
    void dueTriggersUseEquipmentEffectiveRulesOnly() {
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation selectedRegulation = regulation(UUID.randomUUID());
        EquipmentMaintenanceEffectiveRule selectedRule =
                EquipmentMaintenanceEffectiveRule.fromRegulation(equipmentId, selectedRegulation);
        EquipmentMeter meter = meter(equipmentId, MeterType.ENGINE_HOURS, 520.0);

        when(meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId))
                .thenReturn(List.of(meter));
        when(effectiveRuleResolver.resolveApplicable(equipmentId)).thenReturn(List.of(selectedRule));
        when(dueCalculationService.calculate(selectedRule)).thenReturn(new MaintenanceDueCalculationDto(
                equipmentId,
                selectedRegulation.getId(),
                null,
                MaintenanceDueStatus.DUE,
                false,
                true,
                null,
                null,
                null,
                MeterType.ENGINE_HOURS,
                520.0,
                520.0,
                0.0,
                500.0,
                500.0,
                0.0,
                0.0,
                "Meter trigger due"
        ));

        List<MeterTriggerMatch> result = service.dueTriggers(equipmentId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().regulationId()).isEqualTo(selectedRegulation.getId());
        assertThat(result.getFirst().regulationCode()).isEqualTo("MR-SELECTED");
        assertThat(result.getFirst().meterId()).isEqualTo(meter.getId());
        assertThat(result.getFirst().due()).isTrue();
    }

    private MaintenanceRegulation regulation(UUID id) {
        MaintenanceRegulation regulation = new MaintenanceRegulation();
        ReflectionTestUtils.setField(regulation, "id", id);
        regulation.setCode("MR-SELECTED");
        regulation.setName("Selected service");
        regulation.setMaintenanceKind(MaintenanceKind.PREVENTIVE);
        regulation.setNormativeLaborHours(2.0);
        regulation.setActive(true);
        regulation.setPeriodicityUnit(PeriodicityUnit.MONTH);
        regulation.setPeriodicityValue(1);
        regulation.setTriggerMeterType(MeterType.ENGINE_HOURS);
        regulation.setTriggerMeterInterval(500.0);
        regulation.setTriggerPolicy(MaintenanceTriggerPolicy.ANY);
        return regulation;
    }

    private EquipmentMeter meter(UUID equipmentId, MeterType meterType, double currentValue) {
        EquipmentMeter meter = new EquipmentMeter();
        ReflectionTestUtils.setField(meter, "id", UUID.randomUUID());
        meter.setEquipmentId(equipmentId);
        meter.setMeterType(meterType);
        meter.setName("Engine hours");
        meter.setUnit("h");
        meter.setCurrentValue(currentValue);
        meter.setActive(true);
        return meter;
    }
}
