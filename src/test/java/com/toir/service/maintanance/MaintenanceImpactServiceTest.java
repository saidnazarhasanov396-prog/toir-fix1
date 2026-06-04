package com.toir.service.maintanance;

import com.toir.dto.maintenanceplanning.MaintenanceDueCalculationDto;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationRequest;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.enums.AutomationAction;
import com.toir.enums.DuplicatePolicy;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.MaintenanceRecalculationPolicy;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.enums.MeterType;
import com.toir.enums.PeriodicityUnit;
import com.toir.enums.PriorityLevel;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceImpactServiceTest {

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    MaintenanceRegulationRepository regulationRepository;

    @Mock
    MaintenanceDueCalculationService dueCalculationService;

    @Mock
    MaintenanceRegulationApplicabilityService applicabilityService;

    @InjectMocks
    MaintenanceImpactService service;

    @Test
    void previewReturnsAutomationSummaryAndBlockedMeterCounts() {
        UUID equipmentTypeId = UUID.randomUUID();
        Equipment dueEquipment = equipment(UUID.randomUUID(), equipmentTypeId, "EQ-1");
        Equipment blockedEquipment = equipment(UUID.randomUUID(), equipmentTypeId, "EQ-2");
        when(equipmentRepository.findAllForMaintenanceRegulations(equipmentTypeId))
                .thenReturn(List.of(dueEquipment, blockedEquipment));
        when(dueCalculationService.calculate(eq(dueEquipment.getId()), any(MaintenanceRegulation.class)))
                .thenReturn(calculation(dueEquipment.getId(), MaintenanceDueStatus.DUE, "due soon"));
        when(dueCalculationService.calculate(eq(blockedEquipment.getId()), any(MaintenanceRegulation.class)))
                .thenReturn(calculation(blockedEquipment.getId(), MaintenanceDueStatus.BLOCKED, "meter reading missing"));
        when(applicabilityService.evaluate(eq(dueEquipment), any(MaintenanceRegulation.class), any(), any()))
                .thenReturn(new MaintenanceRegulationApplicabilityService.ApplicabilityResult(
                        true, false, false, "due soon", MaintenanceDueStatus.DUE));
        when(applicabilityService.evaluate(eq(blockedEquipment), any(MaintenanceRegulation.class), any(), any()))
                .thenReturn(new MaintenanceRegulationApplicabilityService.ApplicabilityResult(
                        true, true, true, "meter reading missing", MaintenanceDueStatus.BLOCKED));

        var preview = service.preview(request(equipmentTypeId));

        assertThat(preview.affectedEquipment()).isEqualTo(2);
        assertThat(preview.matchedCount()).isEqualTo(2);
        assertThat(preview.blockedCount()).isEqualTo(1);
        assertThat(preview.missingMetersCount()).isEqualTo(1);
        assertThat(preview.duplicatePolicy()).isEqualTo(DuplicatePolicy.ONE_ITEM_PER_CYCLE);
        assertThat(preview.automationSummary()).isEqualTo("Action: CREATE_TASK, approval result: CREATE_TASK, duplicates: ONE_ITEM_PER_CYCLE");
        assertThat(preview.items()).extracting("equipmentCode").containsExactly("EQ-1", "EQ-2");
    }

    @Test
    void impactUsesPersistedRegulationAndKeepsAutomationMetadata() {
        UUID regulationId = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(regulationId, equipmentTypeId);
        Equipment equipment = equipment(UUID.randomUUID(), equipmentTypeId, "EQ-1");
        when(regulationRepository.findByIdAndIsDeletedFalse(regulationId)).thenReturn(Optional.of(regulation));
        when(equipmentRepository.findAllForMaintenanceRegulations(equipmentTypeId)).thenReturn(List.of(equipment));
        when(dueCalculationService.calculate(equipment.getId(), regulation))
                .thenReturn(calculation(equipment.getId(), MaintenanceDueStatus.UPCOMING, "upcoming"));
        when(applicabilityService.evaluate(eq(equipment), eq(regulation), any(), any()))
                .thenReturn(new MaintenanceRegulationApplicabilityService.ApplicabilityResult(
                        true, false, false, "upcoming", MaintenanceDueStatus.UPCOMING));

        var impact = service.impact(regulationId);

        assertThat(impact.regulationId()).isEqualTo(regulationId);
        assertThat(impact.affectedEquipment()).isEqualTo(1);
        assertThat(impact.duplicatePolicy()).isEqualTo(DuplicatePolicy.ONE_OPEN_ITEM_PER_RULE);
        assertThat(impact.automationSummary()).isEqualTo("Action: REQUIRE_APPROVAL, approval result: CREATE_TASK, duplicates: ONE_OPEN_ITEM_PER_RULE");
        assertThat(impact.items()).hasSize(1);
    }

    private MaintenanceRegulationRequest request(UUID equipmentTypeId) {
        return new MaintenanceRegulationRequest(
                "MR-1",
                "Monthly service",
                null,
                equipmentTypeId,
                UUID.randomUUID(),
                MaintenanceKind.PREVENTIVE,
                2.5,
                true,
                PeriodicityUnit.MONTH,
                1,
                3,
                false,
                MeterType.ENGINE_HOURS,
                100.0,
                MaintenanceTriggerPolicy.ANY,
                MaintenanceRecalculationPolicy.FROM_ACTUAL_COMPLETION,
                AutomationAction.CREATE_TASK,
                DuplicatePolicy.ONE_ITEM_PER_CYCLE,
                7,
                10.0,
                UUID.randomUUID(),
                UUID.randomUUID(),
                PriorityLevel.HIGH,
                false,
                null,
                null,
                List.of()
        );
    }

    private MaintenanceRegulation regulation(UUID id, UUID equipmentTypeId) {
        MaintenanceRegulation regulation = new MaintenanceRegulation();
        ReflectionTestUtils.setField(regulation, "id", id);
        regulation.setCode("MR-1");
        regulation.setName("Monthly service");
        regulation.setEquipmentTypeId(equipmentTypeId);
        regulation.setMaintenanceKind(MaintenanceKind.PREVENTIVE);
        regulation.setNormativeLaborHours(2.0);
        regulation.setPeriodicityUnit(PeriodicityUnit.MONTH);
        regulation.setPeriodicityValue(1);
        regulation.setAutomationAction(AutomationAction.REQUIRE_APPROVAL);
        regulation.setDuplicatePolicy(DuplicatePolicy.ONE_OPEN_ITEM_PER_RULE);
        return regulation;
    }

    private Equipment equipment(UUID id, UUID equipmentTypeId, String code) {
        Equipment equipment = new Equipment();
        ReflectionTestUtils.setField(equipment, "id", id);
        equipment.setEquipmentTypeId(equipmentTypeId);
        equipment.setCode(code);
        equipment.setName("Pump " + code);
        return equipment;
    }

    private MaintenanceDueCalculationDto calculation(UUID equipmentId, MaintenanceDueStatus status, String explanation) {
        return new MaintenanceDueCalculationDto(
                equipmentId,
                UUID.randomUUID(),
                null,
                status,
                true,
                false,
                null,
                Instant.parse("2026-06-03T00:00:00Z"),
                Instant.parse("2026-06-03T00:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                explanation
        );
    }
}
