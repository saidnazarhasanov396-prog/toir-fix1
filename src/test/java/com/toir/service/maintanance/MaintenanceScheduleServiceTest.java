package com.toir.service.maintanance;

import com.toir.dto.maintenanceplanning.MaintenanceDueCalculationDto;
import com.toir.dto.maintenanceplanning.MaintenanceDueStructuredExplanationDto;
import com.toir.dto.maintenanceschedule.MaintenanceSchedulePreviewRequest;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.MaintenanceScheduleAnchorMode;
import com.toir.enums.MaintenanceScheduleScopeType;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.enums.PeriodicityUnit;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.service.equipment.OperationalEquipmentPolicy;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceScheduleServiceTest {

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    EquipmentMaintenanceEffectiveRuleResolver ruleResolver;

    @Mock
    MaintenanceDueCalculationService dueCalculationService;

    @Mock
    OperationalEquipmentPolicy operationalEquipmentPolicy;

    MaintenanceScheduleService service;

    @BeforeEach
    void setUp() {
        service = new MaintenanceScheduleService(
                equipmentRepository,
                ruleResolver,
                dueCalculationService,
                operationalEquipmentPolicy,
                ZoneId.of("UTC")
        );
    }

    @Test
    void resetToPlanStartBuildsEveryOccurrenceInsideInclusiveRange() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "EQ-1");
        EquipmentMaintenanceEffectiveRule rule = rule(equipmentId, PeriodicityUnit.MONTH, 1);
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(List.of(equipmentId)))
                .thenReturn(List.of(equipment));
        when(operationalEquipmentPolicy.isOperational(equipment)).thenReturn(true);
        when(ruleResolver.resolveApplicable(equipmentId)).thenReturn(List.of(rule));
        when(dueCalculationService.calculate(rule)).thenReturn(due(null));

        var response = service.preview(new MaintenanceSchedulePreviewRequest(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 4, 1),
                MaintenanceScheduleScopeType.EQUIPMENT,
                List.of(equipmentId),
                null,
                null,
                MaintenanceScheduleAnchorMode.RESET_TO_PLAN_START
        ));

        assertThat(response.items()).extracting(item -> item.plannedDate())
                .containsExactly(
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 3, 1),
                        LocalDate.of(2026, 4, 1)
                );
        assertThat(response.items()).allMatch(item ->
                item.anchorSource().name().equals("PLAN_START"));
        assertThat(response.summary().equipmentCount()).isEqualTo(1);
        assertThat(response.summary().totalOccurrences()).isEqualTo(3);
        assertThat(response.summary().unmatchedCount()).isZero();
    }

    @Test
    void currentModeAdvancesExistingDueDateWithoutResettingDayOfMonth() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "EQ-1");
        EquipmentMaintenanceEffectiveRule rule = rule(equipmentId, PeriodicityUnit.MONTH, 1);
        Instant existingDue = Instant.parse("2025-11-15T09:00:00Z");
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(List.of(equipmentId)))
                .thenReturn(List.of(equipment));
        when(operationalEquipmentPolicy.isOperational(equipment)).thenReturn(true);
        when(ruleResolver.resolveApplicable(equipmentId)).thenReturn(List.of(rule));
        when(dueCalculationService.calculate(rule)).thenReturn(due(existingDue));

        var response = service.preview(new MaintenanceSchedulePreviewRequest(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31),
                MaintenanceScheduleScopeType.EQUIPMENT,
                List.of(equipmentId),
                null,
                null,
                MaintenanceScheduleAnchorMode.CURRENT
        ));

        assertThat(response.items()).extracting(item -> item.plannedDate())
                .containsExactly(
                        LocalDate.of(2026, 1, 15),
                        LocalDate.of(2026, 2, 15),
                        LocalDate.of(2026, 3, 15)
                );
        assertThat(response.items()).allMatch(item ->
                item.anchorSource().name().equals("EXISTING_DUE_DATE"));
    }

    @Test
    void explicitEquipmentScopeRejectsUnknownIdsInsteadOfReturningPartialPreview() {
        UUID existingId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(List.of(existingId, missingId)))
                .thenReturn(List.of(equipment(existingId, "EQ-1")));

        assertThatThrownBy(() -> service.preview(new MaintenanceSchedulePreviewRequest(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                MaintenanceScheduleScopeType.EQUIPMENT,
                List.of(existingId, missingId),
                null,
                null,
                MaintenanceScheduleAnchorMode.CURRENT
        )))
                .isInstanceOfSatisfying(RestException.class, ex ->
                        assertThat(ex.getMessage()).contains(missingId.toString()));
    }

    @Test
    void departmentFilterUsesResponsibleDepartmentBeforePhysicalPlacement() {
        UUID equipmentId = UUID.randomUUID();
        UUID responsibleDepartmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "EQ-1");
        equipment.setResponsibleDepartmentId(responsibleDepartmentId);
        equipment.setDepartmentId(UUID.randomUUID());
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(List.of(equipmentId)))
                .thenReturn(List.of(equipment));
        when(operationalEquipmentPolicy.isOperational(equipment)).thenReturn(true);
        when(ruleResolver.resolveApplicable(equipmentId)).thenReturn(List.of());

        var response = service.preview(new MaintenanceSchedulePreviewRequest(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                MaintenanceScheduleScopeType.EQUIPMENT,
                List.of(equipmentId),
                null,
                responsibleDepartmentId,
                MaintenanceScheduleAnchorMode.CURRENT
        ));

        assertThat(response.summary().unmatchedCount()).isEqualTo(1);
    }

    @Test
    void missingMeterIsCountedWhenCalendarSignalWinsForAnyPolicy() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, "EQ-1");
        EquipmentMaintenanceEffectiveRule rule = rule(equipmentId, PeriodicityUnit.MONTH, 1);
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(List.of(equipmentId)))
                .thenReturn(List.of(equipment));
        when(operationalEquipmentPolicy.isOperational(equipment)).thenReturn(true);
        when(ruleResolver.resolveApplicable(equipmentId)).thenReturn(List.of(rule));
        when(dueCalculationService.calculate(rule)).thenReturn(dueWithSupportingMissingMeter(
                Instant.parse("2026-02-01T09:00:00Z")));

        var response = service.preview(new MaintenanceSchedulePreviewRequest(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31),
                MaintenanceScheduleScopeType.EQUIPMENT,
                List.of(equipmentId),
                null,
                null,
                MaintenanceScheduleAnchorMode.CURRENT
        ));

        assertThat(response.summary().missingMetersCount()).isEqualTo(1);
        assertThat(response.items()).hasSize(2);
    }

    @Test
    void previewRejectsRangesLongerThanAnnualHorizon() {
        assertThatThrownBy(() -> service.preview(new MaintenanceSchedulePreviewRequest(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2036, 1, 1),
                MaintenanceScheduleScopeType.EQUIPMENT,
                List.of(UUID.randomUUID()),
                null,
                null,
                MaintenanceScheduleAnchorMode.CURRENT
        )))
                .isInstanceOfSatisfying(RestException.class, ex ->
                        assertThat(ex.getMessage()).contains("annual horizon"));
    }

    private Equipment equipment(UUID id, String code) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setCode(code);
        equipment.setName("Equipment " + code);
        equipment.setStatus(EquipmentStatus.ACTIVE);
        equipment.setEquipmentTypeId(UUID.randomUUID());
        return equipment;
    }

    private EquipmentMaintenanceEffectiveRule rule(
            UUID equipmentId,
            PeriodicityUnit unit,
            int value
    ) {
        return new EquipmentMaintenanceEffectiveRule(
                equipmentId,
                UUID.randomUUID(),
                null,
                null,
                "MR-1",
                "Monthly maintenance",
                null,
                MaintenanceKind.PREVENTIVE,
                4,
                true,
                unit,
                value,
                0,
                false,
                null,
                null,
                MaintenanceTriggerPolicy.ANY,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                null,
                null
        );
    }

    private MaintenanceDueCalculationDto due(Instant nextCalendarDueAt) {
        return new MaintenanceDueCalculationDto(
                null, null, null, null, false, false, null, nextCalendarDueAt,
                nextCalendarDueAt, null, null, null, null, null, null, null,
                null, null
        );
    }

    private MaintenanceDueCalculationDto dueWithSupportingMissingMeter(Instant nextCalendarDueAt) {
        MaintenanceDueStructuredExplanationDto explanation = new MaintenanceDueStructuredExplanationDto(
                null, null, null, null, null, null, null,
                null, null, 0, MaintenanceTriggerPolicy.ANY, null,
                null, null, null,
                "CALENDAR_UPCOMING",
                "maintenanceDue.reasons.CALENDAR_UPCOMING",
                Map.of(),
                List.of("maintenanceDue.reasons.MISSING_ACTIVE_METER"),
                List.of(Map.of())
        );
        return new MaintenanceDueCalculationDto(
                null, null, null, null, false, false, null, nextCalendarDueAt,
                nextCalendarDueAt, null, null, null, null, null, null, null,
                null, null, explanation
        );
    }
}
