package com.toir.service.maintanance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.maintenanceplanning.MaintenanceDueCalculationDto;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.entity.maintenance.MaintenanceCompletionAnchor;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.MaintenanceInitialSchedulePolicy;
import com.toir.enums.MaintenanceRecalculationPolicy;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.enums.MeterType;
import com.toir.enums.PeriodicityUnit;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.maintenance.MaintenanceCompletionAnchorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceDueCalculationServiceTest {

    @Mock
    EquipmentMeterRepository meterRepository;

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    MaintenanceCompletionAnchorRepository anchorRepository;

    MaintenanceDueCalculationService service;

    @BeforeEach
    void setUp() {
        service = new MaintenanceDueCalculationService(meterRepository, equipmentRepository, anchorRepository, new ObjectMapper());
        ReflectionTestUtils.setField(service, "clock",
                Clock.fixed(Instant.parse("2026-06-02T00:00:00Z"), ZoneOffset.UTC));
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

        assertThat(result.status()).isEqualTo(MaintenanceDueStatus.OVERDUE);
        assertThat(result.dueByCalendar()).isFalse();
        assertThat(result.dueByMeter()).isTrue();
        assertThat(result.meterType()).isEqualTo(MeterType.ENGINE_HOURS);
        assertThat(result.meterCurrentValue()).isEqualTo(520.0);
        assertThat(result.meterInterval()).isEqualTo(500.0);
        assertThat(result.meterAnchorValue()).isZero();
        assertThat(result.meterRemaining()).isEqualTo(-20.0);
        assertThat(result.explanation()).contains("Meter trigger overdue");
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
        assertThat(result.structuredExplanation()).isNotNull();
        assertThat(result.structuredExplanation().blockingCode()).isEqualTo("MISSING_ACTIVE_METER");
        assertThat(result.structuredExplanation().blockingField()).isEqualTo("ENGINE_HOURS");
        assertThat(result.structuredExplanation().fixLink()).isEqualTo("/equipment/%s/meters".formatted(equipmentId));
        assertThat(result.structuredExplanation().reasonText()).contains("Required active meter");
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
        meter.setCurrentValue(250.0);

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
        assertThat(result.meterCurrentValue()).isEqualTo(250.0);
        assertThat(result.explanation()).contains("Meter trigger due");
        assertThat(result.structuredExplanation().baseSource()).isEqualTo("COMPLETION_ANCHOR");
        assertThat(result.structuredExplanation().lastCompletionDate()).isEqualTo(anchor.getPerformedAt());
        assertThat(result.structuredExplanation().meterType()).isEqualTo(MeterType.ENGINE_HOURS);
        assertThat(result.structuredExplanation().currentMeterValue()).isEqualTo(250.0);
        assertThat(result.structuredExplanation().intervalMeterValue()).isEqualTo(100.0);
        assertThat(result.structuredExplanation().remainingMeterValue()).isZero();
        assertThat(result.structuredExplanation().triggerPolicy()).isEqualTo(MaintenanceTriggerPolicy.ANY);
    }

    @Test
    void meterCycleAfterRepairResetStartsFromFailureTimeAnchor() {
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setTriggerMeterType(MeterType.ENGINE_HOURS);
        regulation.setTriggerMeterInterval(500.0);

        EquipmentMeter meter = meter(equipmentId, MeterType.ENGINE_HOURS, 1400.0);
        MaintenanceCompletionAnchor anchor = anchor(
                equipmentId,
                regulation.getId(),
                Instant.parse("2026-06-17T04:00:00Z"),
                1320.0
        );

        when(meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId))
                .thenReturn(java.util.List.of(meter));
        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null))
                .thenReturn(Optional.of(anchor));

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.status()).isEqualTo(MaintenanceDueStatus.NOT_DUE);
        assertThat(result.meterAnchorValue()).isEqualTo(1320.0);
        assertThat(result.meterCurrentValue()).isEqualTo(1400.0);
        assertThat(result.nextMeterDueValue()).isEqualTo(1820.0);
        assertThat(result.meterRemaining()).isEqualTo(420.0);
    }

    @Test
    void initialMeterBaselineAtFourThousandGivesNextDueAtNineteenThousand() {
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setTriggerMeterType(MeterType.MILEAGE_KM);
        regulation.setTriggerMeterInterval(15_000.0);
        EquipmentMeter meter = meter(equipmentId, MeterType.MILEAGE_KM, 4_000.0);
        MaintenanceCompletionAnchor anchor = meterAnchor(
                equipmentId,
                regulation.getId(),
                Instant.parse("2026-06-17T00:00:00Z"),
                MeterType.MILEAGE_KM,
                4_000.0
        );

        when(meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId))
                .thenReturn(java.util.List.of(meter));
        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null))
                .thenReturn(Optional.of(anchor));

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.status()).isEqualTo(MaintenanceDueStatus.NOT_DUE);
        assertThat(result.meterAnchorValue()).isEqualTo(4_000.0);
        assertThat(result.nextMeterDueValue()).isEqualTo(19_000.0);
        assertThat(result.meterRemaining()).isEqualTo(15_000.0);
    }

    @Test
    void oilChangeCompletionAtEightThousandGivesNextDueAtTwentyThreeThousand() {
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setTriggerMeterType(MeterType.MILEAGE_KM);
        regulation.setTriggerMeterInterval(15_000.0);
        EquipmentMeter meter = meter(equipmentId, MeterType.MILEAGE_KM, 8_000.0);
        MaintenanceCompletionAnchor anchor = meterAnchor(
                equipmentId,
                regulation.getId(),
                Instant.parse("2026-06-17T08:45:00Z"),
                MeterType.MILEAGE_KM,
                8_000.0
        );

        when(meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId))
                .thenReturn(java.util.List.of(meter));
        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null))
                .thenReturn(Optional.of(anchor));

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.status()).isEqualTo(MaintenanceDueStatus.NOT_DUE);
        assertThat(result.meterAnchorValue()).isEqualTo(8_000.0);
        assertThat(result.nextMeterDueValue()).isEqualTo(23_000.0);
        assertThat(result.meterRemaining()).isEqualTo(15_000.0);
    }

    @Test
    void meterExactThresholdIsDueNotOverdue() {
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setTriggerMeterType(MeterType.MILEAGE_KM);
        regulation.setTriggerMeterInterval(1000.0);

        when(meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId))
                .thenReturn(java.util.List.of(meter(equipmentId, MeterType.MILEAGE_KM, 1000.0)));
        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.empty());

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.status()).isEqualTo(MaintenanceDueStatus.DUE);
        assertThat(result.dueByMeter()).isTrue();
        assertThat(result.meterAnchorValue()).isZero();
        assertThat(result.meterRemaining()).isZero();
        assertThat(result.nextMeterDueValue()).isEqualTo(1000.0);
        assertThat(result.explanation()).contains("Meter trigger due");
        assertThat(result.explanation()).doesNotContain("Meter trigger overdue");
    }

    @Test
    void meterBelowThresholdInsideLeadWindowIsUpcoming() {
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setTriggerMeterType(MeterType.MILEAGE_KM);
        regulation.setTriggerMeterInterval(1000.0);
        regulation.setLeadMeterPercent(5.0);

        when(meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId))
                .thenReturn(java.util.List.of(meter(equipmentId, MeterType.MILEAGE_KM, 950.0)));
        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.empty());

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.status()).isEqualTo(MaintenanceDueStatus.UPCOMING);
        assertThat(result.dueByMeter()).isTrue();
        assertThat(result.meterRemaining()).isEqualTo(50.0);
        assertThat(result.nextMeterDueValue()).isEqualTo(1000.0);
        assertThat(result.explanation()).contains("Meter trigger upcoming");
    }

    @Test
    void meterOverThresholdIsOverdueWithNegativeRemaining() {
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setTriggerMeterType(MeterType.MILEAGE_KM);
        regulation.setTriggerMeterInterval(1000.0);

        when(meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId))
                .thenReturn(java.util.List.of(meter(equipmentId, MeterType.MILEAGE_KM, 1050.0)));
        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.empty());

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.status()).isEqualTo(MaintenanceDueStatus.OVERDUE);
        assertThat(result.dueByMeter()).isTrue();
        assertThat(result.meterRemaining()).isEqualTo(-50.0);
        assertThat(result.nextMeterDueValue()).isEqualTo(1000.0);
        assertThat(result.explanation()).contains("Meter trigger overdue");
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

    @Test
    void calendarDueTenDaysInFutureWithThreeDayLeadIsNotDue() {
        ReflectionTestUtils.setField(service, "clock",
                Clock.fixed(Instant.parse("2026-06-02T12:00:00Z"), ZoneOffset.UTC));
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setPeriodicityUnit(PeriodicityUnit.DAY);
        regulation.setPeriodicityValue(10);
        regulation.setLeadTimeDays(3);
        MaintenanceCompletionAnchor anchor = anchor(equipmentId, regulation.getId(),
                Instant.parse("2026-06-02T00:00:00Z"), 0.0);

        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.of(anchor));

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.status()).isEqualTo(MaintenanceDueStatus.NOT_DUE);
        assertThat(result.dueByCalendar()).isFalse();
        assertThat(result.nextDueAt()).isEqualTo(Instant.parse("2026-06-12T00:00:00Z"));
        assertThat(result.explanation()).contains("Calendar trigger not due");
    }

    @Test
    void calendarDueTwoDaysInFutureWithThreeDayLeadIsUpcoming() {
        ReflectionTestUtils.setField(service, "clock",
                Clock.fixed(Instant.parse("2026-06-02T12:00:00Z"), ZoneOffset.UTC));
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setPeriodicityUnit(PeriodicityUnit.DAY);
        regulation.setPeriodicityValue(2);
        regulation.setLeadTimeDays(3);
        MaintenanceCompletionAnchor anchor = anchor(equipmentId, regulation.getId(),
                Instant.parse("2026-06-02T00:00:00Z"), 0.0);

        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.of(anchor));

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.status()).isEqualTo(MaintenanceDueStatus.UPCOMING);
        assertThat(result.dueByCalendar()).isTrue();
        assertThat(result.nextDueAt()).isEqualTo(Instant.parse("2026-06-04T00:00:00Z"));
    }

    @Test
    void calendarDueTodayIsDueNotOverdue() {
        ReflectionTestUtils.setField(service, "clock",
                Clock.fixed(Instant.parse("2026-06-02T12:00:00Z"), ZoneOffset.UTC));
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setPeriodicityUnit(PeriodicityUnit.DAY);
        regulation.setPeriodicityValue(1);
        MaintenanceCompletionAnchor anchor = anchor(equipmentId, regulation.getId(),
                Instant.parse("2026-06-01T00:00:00Z"), 0.0);

        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.of(anchor));

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.status()).isEqualTo(MaintenanceDueStatus.DUE);
        assertThat(result.dueByCalendar()).isTrue();
        assertThat(result.nextDueAt()).isEqualTo(Instant.parse("2026-06-02T00:00:00Z"));
        assertThat(result.explanation()).doesNotContain("overdue");
    }

    @Test
    void calendarDueYesterdayIsOverdue() {
        ReflectionTestUtils.setField(service, "clock",
                Clock.fixed(Instant.parse("2026-06-02T12:00:00Z"), ZoneOffset.UTC));
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setPeriodicityUnit(PeriodicityUnit.DAY);
        regulation.setPeriodicityValue(1);
        MaintenanceCompletionAnchor anchor = anchor(equipmentId, regulation.getId(),
                Instant.parse("2026-05-31T00:00:00Z"), 0.0);

        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.of(anchor));

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.status()).isEqualTo(MaintenanceDueStatus.OVERDUE);
        assertThat(result.dueByCalendar()).isTrue();
        assertThat(result.nextDueAt()).isEqualTo(Instant.parse("2026-06-01T00:00:00Z"));
        assertThat(result.structuredExplanation().baseSource()).isEqualTo("COMPLETION_ANCHOR");
        assertThat(result.structuredExplanation().baseDate()).isEqualTo(anchor.getPerformedAt());
        assertThat(result.structuredExplanation().intervalDays()).isEqualTo(1);
        assertThat(result.structuredExplanation().toleranceDays()).isZero();
        assertThat(result.structuredExplanation().reasonText()).contains("Calendar trigger overdue");
    }

    @Test
    void monthlyCalendarFromOperationStartWithFutureDueDateIsNotOverdue() {
        ReflectionTestUtils.setField(service, "clock",
                Clock.fixed(Instant.parse("2026-06-02T12:00:00Z"), ZoneOffset.UTC));
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setInitialSchedulePolicy(MaintenanceInitialSchedulePolicy.FROM_OPERATION_START);
        regulation.setPeriodicityUnit(PeriodicityUnit.MONTH);
        regulation.setPeriodicityValue(1);
        regulation.setLeadTimeDays(3);
        equipment(equipmentId, LocalDate.parse("2026-05-20"));

        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.empty());

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.status()).isEqualTo(MaintenanceDueStatus.NOT_DUE);
        assertThat(result.dueByCalendar()).isFalse();
        assertThat(result.nextDueAt()).isEqualTo(Instant.parse("2026-06-20T00:00:00Z"));
        assertThat(result.explanation()).doesNotContain("overdue");
    }

    @Test
    void combinedPoliciesDoNotConvertFutureCalendarSignalToOverdue() {
        ReflectionTestUtils.setField(service, "clock",
                Clock.fixed(Instant.parse("2026-06-02T12:00:00Z"), ZoneOffset.UTC));

        UUID anyEquipmentId = UUID.randomUUID();
        MaintenanceRegulation anyRegulation = regulation(anyEquipmentId);
        anyRegulation.setTriggerPolicy(MaintenanceTriggerPolicy.ANY);
        anyRegulation.setPeriodicityUnit(PeriodicityUnit.DAY);
        anyRegulation.setPeriodicityValue(10);
        anyRegulation.setLeadTimeDays(3);
        anyRegulation.setTriggerMeterType(MeterType.ENGINE_HOURS);
        anyRegulation.setTriggerMeterInterval(500.0);
        MaintenanceCompletionAnchor anyAnchor = anchor(anyEquipmentId, anyRegulation.getId(),
                Instant.parse("2026-06-02T00:00:00Z"), 0.0);
        when(anchorRepository.findLatestAnchor(anyEquipmentId, anyRegulation.getId(), null))
                .thenReturn(Optional.of(anyAnchor));
        when(meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(anyEquipmentId))
                .thenReturn(java.util.List.of(meter(anyEquipmentId, MeterType.ENGINE_HOURS, 520.0)));

        MaintenanceDueCalculationDto anyResult = service.calculate(anyEquipmentId, anyRegulation);

        assertThat(anyResult.status()).isEqualTo(MaintenanceDueStatus.OVERDUE);
        assertThat(anyResult.dueByCalendar()).isFalse();
        assertThat(anyResult.explanation()).contains("Meter trigger overdue");
        assertThat(anyResult.explanation()).contains("Calendar trigger not due");
        assertThat(anyResult.explanation()).doesNotContain("Calendar trigger overdue");

        UUID allEquipmentId = UUID.randomUUID();
        MaintenanceRegulation allRegulation = regulation(allEquipmentId);
        allRegulation.setTriggerPolicy(MaintenanceTriggerPolicy.ALL);
        allRegulation.setPeriodicityUnit(PeriodicityUnit.DAY);
        allRegulation.setPeriodicityValue(10);
        allRegulation.setLeadTimeDays(3);
        allRegulation.setTriggerMeterType(MeterType.ENGINE_HOURS);
        allRegulation.setTriggerMeterInterval(500.0);
        MaintenanceCompletionAnchor allAnchor = anchor(allEquipmentId, allRegulation.getId(),
                Instant.parse("2026-06-02T00:00:00Z"), 0.0);
        when(anchorRepository.findLatestAnchor(allEquipmentId, allRegulation.getId(), null))
                .thenReturn(Optional.of(allAnchor));
        when(meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(allEquipmentId))
                .thenReturn(java.util.List.of(meter(allEquipmentId, MeterType.ENGINE_HOURS, 520.0)));

        MaintenanceDueCalculationDto allResult = service.calculate(allEquipmentId, allRegulation);

        assertThat(allResult.status()).isEqualTo(MaintenanceDueStatus.NOT_DUE);
        assertThat(allResult.dueByCalendar()).isFalse();
        assertThat(allResult.explanation()).contains("Calendar trigger not due");
        assertThat(allResult.explanation()).doesNotContain("Calendar trigger overdue");
    }

    @Test
    void combinedAnyWithExactMeterThresholdAndCalendarNotDueIsDueNotOverdue() {
        ReflectionTestUtils.setField(service, "clock",
                Clock.fixed(Instant.parse("2026-06-02T12:00:00Z"), ZoneOffset.UTC));
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setTriggerPolicy(MaintenanceTriggerPolicy.ANY);
        regulation.setInitialSchedulePolicy(MaintenanceInitialSchedulePolicy.FROM_REGULATION_CREATED);
        regulation.setCreatedAt(Instant.parse("2026-05-10T00:00:00Z"));
        regulation.setTriggerMeterType(MeterType.MILEAGE_KM);
        regulation.setTriggerMeterInterval(1000.0);

        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.empty());
        when(meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId))
                .thenReturn(java.util.List.of(meter(equipmentId, MeterType.MILEAGE_KM, 1000.0)));

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.status()).isEqualTo(MaintenanceDueStatus.DUE);
        assertThat(result.dueByMeter()).isTrue();
        assertThat(result.dueByCalendar()).isFalse();
        assertThat(result.meterRemaining()).isZero();
        assertThat(result.explanation()).contains("Meter trigger due");
        assertThat(result.explanation()).contains("Calendar trigger not due from regulation created");
        assertThat(result.explanation()).doesNotContain("Meter trigger overdue");
    }

    @Test
    void combinedAnyUsesMeterWhenMeterDueAndCalendarNotDue() {
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setTriggerPolicy(MaintenanceTriggerPolicy.ANY);
        regulation.setTriggerMeterType(MeterType.ENGINE_HOURS);
        regulation.setTriggerMeterInterval(500.0);

        EquipmentMeter meter = meter(equipmentId, MeterType.ENGINE_HOURS, 500.0);
        MaintenanceCompletionAnchor anchor = anchor(equipmentId, regulation.getId(), Instant.now(), 0.0);

        when(meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId)).thenReturn(java.util.List.of(meter));
        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.of(anchor));

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.status()).isEqualTo(MaintenanceDueStatus.DUE);
        assertThat(result.dueByMeter()).isTrue();
        assertThat(result.dueByCalendar()).isFalse();
        assertThat(result.explanation()).contains("Meter trigger due");
    }

    @Test
    void combinedAllDoesNotBecomeDueWhenOnlyMeterIsDue() {
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setTriggerPolicy(MaintenanceTriggerPolicy.ALL);
        regulation.setTriggerMeterType(MeterType.ENGINE_HOURS);
        regulation.setTriggerMeterInterval(500.0);

        EquipmentMeter meter = meter(equipmentId, MeterType.ENGINE_HOURS, 500.0);
        MaintenanceCompletionAnchor anchor = anchor(equipmentId, regulation.getId(), Instant.now(), 0.0);

        when(meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId)).thenReturn(java.util.List.of(meter));
        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.of(anchor));

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.status()).isEqualTo(MaintenanceDueStatus.NOT_DUE);
        assertThat(result.dueByMeter()).isTrue();
        assertThat(result.dueByCalendar()).isFalse();
        assertThat(result.explanation()).contains("Waiting for all maintenance triggers");
    }

    @Test
    void combinedAllBecomesDueWhenMeterAndCalendarAreBothDue() {
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setPeriodicityUnit(PeriodicityUnit.DAY);
        regulation.setPeriodicityValue(1);
        regulation.setTriggerPolicy(MaintenanceTriggerPolicy.ALL);
        regulation.setTriggerMeterType(MeterType.ENGINE_HOURS);
        regulation.setTriggerMeterInterval(500.0);

        EquipmentMeter meter = meter(equipmentId, MeterType.ENGINE_HOURS, 500.0);
        MaintenanceCompletionAnchor anchor = anchor(equipmentId, regulation.getId(),
                Instant.parse("2026-06-01T00:00:00Z"), 0.0);

        when(meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId)).thenReturn(java.util.List.of(meter));
        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.of(anchor));

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.status()).isIn(MaintenanceDueStatus.DUE, MaintenanceDueStatus.OVERDUE);
        assertThat(result.dueByMeter()).isTrue();
        assertThat(result.dueByCalendar()).isTrue();
    }

    @Test
    void combinedAnyProceedsWhenCalendarBlockedButMeterDue() {
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setInitialSchedulePolicy(MaintenanceInitialSchedulePolicy.BLOCKED);
        regulation.setTriggerPolicy(MaintenanceTriggerPolicy.ANY);
        regulation.setTriggerMeterType(MeterType.ENGINE_HOURS);
        regulation.setTriggerMeterInterval(500.0);

        EquipmentMeter meter = meter(equipmentId, MeterType.ENGINE_HOURS, 500.0);

        when(meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId)).thenReturn(java.util.List.of(meter));
        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.empty());

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.status()).isEqualTo(MaintenanceDueStatus.DUE);
        assertThat(result.dueByMeter()).isTrue();
        assertThat(result.dueByCalendar()).isFalse();
        assertThat(result.explanation()).contains("Meter trigger due");
        assertThat(result.explanation()).contains("No completion anchor for calendar trigger.");
    }

    @Test
    void combinedAllBlocksWhenCalendarBlockedEvenIfMeterDue() {
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setInitialSchedulePolicy(MaintenanceInitialSchedulePolicy.BLOCKED);
        regulation.setTriggerPolicy(MaintenanceTriggerPolicy.ALL);
        regulation.setTriggerMeterType(MeterType.ENGINE_HOURS);
        regulation.setTriggerMeterInterval(500.0);

        EquipmentMeter meter = meter(equipmentId, MeterType.ENGINE_HOURS, 500.0);

        when(meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId)).thenReturn(java.util.List.of(meter));
        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.empty());

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.status()).isEqualTo(MaintenanceDueStatus.BLOCKED);
        assertThat(result.dueByMeter()).isTrue();
        assertThat(result.dueByCalendar()).isFalse();
        assertThat(result.explanation()).contains("No completion anchor for calendar trigger.");
    }

    @Test
    void calendarFirstRunFromOperationStartDateDoesNotBlock() {
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setInitialSchedulePolicy(MaintenanceInitialSchedulePolicy.FROM_OPERATION_START);
        equipment(equipmentId, LocalDate.parse("2026-05-01"));

        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.empty());

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.status()).isIn(MaintenanceDueStatus.DUE, MaintenanceDueStatus.OVERDUE);
        assertThat(result.dueByCalendar()).isTrue();
        assertThat(result.nextDueAt()).isEqualTo(Instant.parse("2026-06-01T00:00:00Z"));
        assertThat(result.explanation()).doesNotContain("blocked");
    }

    @Test
    void calendarFirstRunFromRegulationCreatedAt() {
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setInitialSchedulePolicy(MaintenanceInitialSchedulePolicy.FROM_REGULATION_CREATED);
        regulation.setCreatedAt(Instant.parse("2026-05-10T12:00:00Z"));

        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.empty());

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.status()).isEqualTo(MaintenanceDueStatus.NOT_DUE);
        assertThat(result.nextDueAt()).isEqualTo(Instant.parse("2026-06-10T12:00:00Z"));
        assertThat(result.explanation()).contains("regulation created");
    }

    @Test
    void calendarRequireInitialAnchorBlocksWithoutDownstreamAnchorFallback() {
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setInitialSchedulePolicy(MaintenanceInitialSchedulePolicy.REQUIRE_INITIAL_ANCHOR);

        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.empty());

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.status()).isEqualTo(MaintenanceDueStatus.BLOCKED);
        assertThat(result.dueByCalendar()).isFalse();
        assertThat(result.nextDueAt()).isNull();
        assertThat(result.explanation()).isEqualTo("Initial completion anchor is required for this calendar regulation.");
    }

    @Test
    void calendarBlockedPolicyUsesNoAnchorExplanation() {
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setInitialSchedulePolicy(MaintenanceInitialSchedulePolicy.BLOCKED);

        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.empty());

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.status()).isEqualTo(MaintenanceDueStatus.BLOCKED);
        assertThat(result.explanation()).isEqualTo("No completion anchor for calendar trigger.");
    }

    @Test
    void calendarWithCompletionAnchorIgnoresOperationStartDateFallback() {
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setInitialSchedulePolicy(MaintenanceInitialSchedulePolicy.FROM_OPERATION_START);
        MaintenanceCompletionAnchor anchor = anchor(
                equipmentId,
                regulation.getId(),
                Instant.parse("2026-05-01T00:00:00Z"),
                0.0
        );

        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.of(anchor));

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.nextDueAt()).isEqualTo(Instant.parse("2026-06-01T00:00:00Z"));
        assertThat(result.lastPerformedAt()).isEqualTo(anchor.getPerformedAt());
    }

    @Test
    void combinedAllWithCalendarFallbackNotDueDoesNotBecomeDueWhenOnlyMeterIsDue() {
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setInitialSchedulePolicy(MaintenanceInitialSchedulePolicy.FROM_OPERATION_START);
        regulation.setTriggerPolicy(MaintenanceTriggerPolicy.ALL);
        regulation.setTriggerMeterType(MeterType.ENGINE_HOURS);
        regulation.setTriggerMeterInterval(500.0);
        equipment(equipmentId, LocalDate.parse("2026-05-20"));
        EquipmentMeter meter = meter(equipmentId, MeterType.ENGINE_HOURS, 500.0);

        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.empty());
        when(meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId)).thenReturn(java.util.List.of(meter));

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.status()).isEqualTo(MaintenanceDueStatus.NOT_DUE);
        assertThat(result.dueByMeter()).isTrue();
        assertThat(result.dueByCalendar()).isFalse();
    }

    @Test
    void combinedAnyWithCalendarFallbackNotDueProceedsWhenMeterIsDue() {
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setInitialSchedulePolicy(MaintenanceInitialSchedulePolicy.FROM_OPERATION_START);
        regulation.setTriggerPolicy(MaintenanceTriggerPolicy.ANY);
        regulation.setTriggerMeterType(MeterType.ENGINE_HOURS);
        regulation.setTriggerMeterInterval(500.0);
        equipment(equipmentId, LocalDate.parse("2026-05-20"));
        EquipmentMeter meter = meter(equipmentId, MeterType.ENGINE_HOURS, 500.0);

        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.empty());
        when(meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId)).thenReturn(java.util.List.of(meter));

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.status()).isEqualTo(MaintenanceDueStatus.DUE);
        assertThat(result.dueByMeter()).isTrue();
        assertThat(result.dueByCalendar()).isFalse();
    }

    @Test
    void manualPolicyExposesReasonKey() {
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setTriggerPolicy(MaintenanceTriggerPolicy.MANUAL);

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.structuredExplanation().reasonCode()).isEqualTo("MANUAL_TRIGGER_POLICY");
        assertThat(result.structuredExplanation().reasonKey()).isEqualTo("maintenanceDue.reasons.MANUAL_TRIGGER_POLICY");
        assertThat(result.structuredExplanation().reasonParams()).isEmpty();
    }

    @Test
    void noTriggerConfiguredExposesReasonKey() {
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setPeriodicityUnit(null);
        regulation.setPeriodicityValue(0);

        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.empty());

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.status()).isEqualTo(MaintenanceDueStatus.NOT_DUE);
        assertThat(result.structuredExplanation().reasonCode()).isEqualTo("NO_TRIGGER_CONFIGURED");
        assertThat(result.structuredExplanation().reasonKey()).isEqualTo("maintenanceDue.reasons.NO_TRIGGER_CONFIGURED");
    }

    @Test
    void missingActiveMeterExposesMeterTypeParam() {
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setPeriodicityUnit(null);
        regulation.setPeriodicityValue(0);
        regulation.setTriggerMeterType(MeterType.ENGINE_HOURS);
        regulation.setTriggerMeterInterval(100.0);

        when(meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId)).thenReturn(java.util.List.of());
        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.empty());

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.structuredExplanation().reasonCode()).isEqualTo("MISSING_ACTIVE_METER");
        assertThat(result.structuredExplanation().reasonKey()).isEqualTo("maintenanceDue.reasons.MISSING_ACTIVE_METER");
        assertThat(result.structuredExplanation().reasonParams()).containsEntry("meterType", "ENGINE_HOURS");
    }

    @Test
    void calendarOverdueExposesBaseSourceParam() {
        ReflectionTestUtils.setField(service, "clock",
                Clock.fixed(Instant.parse("2026-06-02T12:00:00Z"), ZoneOffset.UTC));
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setPeriodicityUnit(PeriodicityUnit.DAY);
        regulation.setPeriodicityValue(1);
        MaintenanceCompletionAnchor anchor = anchor(equipmentId, regulation.getId(),
                Instant.parse("2026-05-31T00:00:00Z"), 0.0);

        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.of(anchor));

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.structuredExplanation().reasonCode()).isEqualTo("CALENDAR_OVERDUE");
        assertThat(result.structuredExplanation().reasonKey()).isEqualTo("maintenanceDue.reasons.CALENDAR_OVERDUE");
        assertThat(result.structuredExplanation().baseSource()).isEqualTo("COMPLETION_ANCHOR");
        // COMPLETION_ANCHOR base carries no "from X" text suffix, but the reason param is still
        // populated so the frontend has a self-contained (key, params) pair to translate from.
        assertThat(result.structuredExplanation().reasonParams()).containsEntry("baseSource", "COMPLETION_ANCHOR");
    }

    @Test
    void calendarNotDueFromRegulationCreatedExposesBaseSourceParam() {
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setInitialSchedulePolicy(MaintenanceInitialSchedulePolicy.FROM_REGULATION_CREATED);
        regulation.setCreatedAt(Instant.parse("2026-05-10T12:00:00Z"));

        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.empty());

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.structuredExplanation().reasonCode()).isEqualTo("CALENDAR_NOT_DUE");
        assertThat(result.structuredExplanation().reasonParams()).containsEntry("baseSource", "REGULATION_CREATED");
        assertThat(result.structuredExplanation().baseSource()).isEqualTo("REGULATION_CREATED");
    }

    @Test
    void calendarFromOperationStartExposesBaseSourceParam() {
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setInitialSchedulePolicy(MaintenanceInitialSchedulePolicy.FROM_OPERATION_START);
        equipment(equipmentId, LocalDate.parse("2026-05-01"));

        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.empty());

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.structuredExplanation().reasonParams()).containsEntry("baseSource", "OPERATION_START");
        assertThat(result.structuredExplanation().baseSource()).isEqualTo("OPERATION_START");
    }

    @Test
    void requireInitialAnchorExposesReasonKeyAndBlockingCode() {
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setInitialSchedulePolicy(MaintenanceInitialSchedulePolicy.REQUIRE_INITIAL_ANCHOR);

        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.empty());

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.structuredExplanation().reasonCode()).isEqualTo("REQUIRE_INITIAL_ANCHOR");
        assertThat(result.structuredExplanation().reasonKey()).isEqualTo("maintenanceDue.reasons.REQUIRE_INITIAL_ANCHOR");
        assertThat(result.structuredExplanation().blockingCode()).isEqualTo("MISSING_COMPLETION_ANCHOR");
        assertThat(result.structuredExplanation().blockingField()).isEqualTo("completionAnchor");
    }

    @Test
    void noCompletionAnchorExposesReasonKeyAndBlockingCode() {
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setInitialSchedulePolicy(MaintenanceInitialSchedulePolicy.BLOCKED);

        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.empty());

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.structuredExplanation().reasonCode()).isEqualTo("NO_COMPLETION_ANCHOR");
        assertThat(result.structuredExplanation().blockingCode()).isEqualTo("MISSING_COMPLETION_ANCHOR");
    }

    @Test
    void meterReasonCodesCoverAllFourMeterStatuses() {
        UUID overdueId = UUID.randomUUID();
        MaintenanceRegulation overdueRegulation = regulation(overdueId);
        overdueRegulation.setTriggerMeterType(MeterType.MILEAGE_KM);
        overdueRegulation.setTriggerMeterInterval(1000.0);
        when(meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(overdueId))
                .thenReturn(java.util.List.of(meter(overdueId, MeterType.MILEAGE_KM, 1050.0)));
        when(anchorRepository.findLatestAnchor(overdueId, overdueRegulation.getId(), null)).thenReturn(Optional.empty());
        assertThat(service.calculate(overdueId, overdueRegulation).structuredExplanation().reasonCode())
                .isEqualTo("METER_OVERDUE");

        UUID dueId = UUID.randomUUID();
        MaintenanceRegulation dueRegulation = regulation(dueId);
        dueRegulation.setTriggerMeterType(MeterType.MILEAGE_KM);
        dueRegulation.setTriggerMeterInterval(1000.0);
        when(meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(dueId))
                .thenReturn(java.util.List.of(meter(dueId, MeterType.MILEAGE_KM, 1000.0)));
        when(anchorRepository.findLatestAnchor(dueId, dueRegulation.getId(), null)).thenReturn(Optional.empty());
        assertThat(service.calculate(dueId, dueRegulation).structuredExplanation().reasonCode())
                .isEqualTo("METER_DUE");

        UUID upcomingId = UUID.randomUUID();
        MaintenanceRegulation upcomingRegulation = regulation(upcomingId);
        upcomingRegulation.setTriggerMeterType(MeterType.MILEAGE_KM);
        upcomingRegulation.setTriggerMeterInterval(1000.0);
        upcomingRegulation.setLeadMeterPercent(5.0);
        when(meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(upcomingId))
                .thenReturn(java.util.List.of(meter(upcomingId, MeterType.MILEAGE_KM, 950.0)));
        when(anchorRepository.findLatestAnchor(upcomingId, upcomingRegulation.getId(), null)).thenReturn(Optional.empty());
        assertThat(service.calculate(upcomingId, upcomingRegulation).structuredExplanation().reasonCode())
                .isEqualTo("METER_UPCOMING");

        UUID notDueId = UUID.randomUUID();
        MaintenanceRegulation notDueRegulation = regulation(notDueId);
        // Both signals are inactive (NOT_DUE) in this case, so combine() has no dominant signal to
        // pick - disable the calendar trigger so the meter reason is unambiguously primary.
        notDueRegulation.setPeriodicityUnit(null);
        notDueRegulation.setPeriodicityValue(0);
        notDueRegulation.setTriggerMeterType(MeterType.MILEAGE_KM);
        notDueRegulation.setTriggerMeterInterval(1000.0);
        when(meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(notDueId))
                .thenReturn(java.util.List.of(meter(notDueId, MeterType.MILEAGE_KM, 100.0)));
        when(anchorRepository.findLatestAnchor(notDueId, notDueRegulation.getId(), null)).thenReturn(Optional.empty());
        assertThat(service.calculate(notDueId, notDueRegulation).structuredExplanation().reasonCode())
                .isEqualTo("METER_NOT_DUE");
    }

    @Test
    void combinedAllNoneDueExposesWaitingReasonKeyWithSupportingReasons() {
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setTriggerPolicy(MaintenanceTriggerPolicy.ALL);
        regulation.setTriggerMeterType(MeterType.ENGINE_HOURS);
        regulation.setTriggerMeterInterval(500.0);

        EquipmentMeter meter = meter(equipmentId, MeterType.ENGINE_HOURS, 500.0);
        MaintenanceCompletionAnchor anchor = anchor(equipmentId, regulation.getId(), Instant.now(), 0.0);

        when(meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId)).thenReturn(java.util.List.of(meter));
        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.of(anchor));

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.status()).isEqualTo(MaintenanceDueStatus.NOT_DUE);
        assertThat(result.structuredExplanation().reasonCode()).isEqualTo("WAITING_ALL_NONE_DUE");
        assertThat(result.structuredExplanation().reasonKey()).isEqualTo("maintenanceDue.reasons.WAITING_ALL_NONE_DUE");
        assertThat(result.structuredExplanation().supportingReasonKeys())
                .contains("maintenanceDue.reasons.CALENDAR_NOT_DUE", "maintenanceDue.reasons.METER_DUE");
    }

    @Test
    void combinedAllUpcomingExposesWaitingUpcomingReasonKey() {
        ReflectionTestUtils.setField(service, "clock",
                Clock.fixed(Instant.parse("2026-06-02T12:00:00Z"), ZoneOffset.UTC));
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setTriggerPolicy(MaintenanceTriggerPolicy.ALL);
        regulation.setPeriodicityUnit(PeriodicityUnit.DAY);
        regulation.setPeriodicityValue(2);
        regulation.setLeadTimeDays(3);
        regulation.setTriggerMeterType(MeterType.MILEAGE_KM);
        regulation.setTriggerMeterInterval(1000.0);
        regulation.setLeadMeterPercent(5.0);

        MaintenanceCompletionAnchor anchor = anchor(equipmentId, regulation.getId(),
                Instant.parse("2026-06-02T00:00:00Z"), 0.0);
        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.of(anchor));
        when(meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId))
                .thenReturn(java.util.List.of(meter(equipmentId, MeterType.MILEAGE_KM, 950.0)));

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.status()).isEqualTo(MaintenanceDueStatus.UPCOMING);
        assertThat(result.structuredExplanation().reasonCode()).isEqualTo("WAITING_ALL_UPCOMING");
        assertThat(result.structuredExplanation().reasonKey()).isEqualTo("maintenanceDue.reasons.WAITING_ALL_UPCOMING");
    }

    @Test
    void combinedAllBlockedByMissingMeterIsDetectedEvenWhenCalendarIsPrimaryReason() {
        // Regression guard: policy=ALL picks the calendar signal as the primary/dominant reason
        // (calendar is evaluated first), but the actual blocking cause here is the missing meter.
        // blockingCode must be derived by scanning the whole reason set, not just the primary code.
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setTriggerPolicy(MaintenanceTriggerPolicy.ALL);
        regulation.setPeriodicityUnit(PeriodicityUnit.DAY);
        regulation.setPeriodicityValue(10);
        regulation.setTriggerMeterType(MeterType.ENGINE_HOURS);
        regulation.setTriggerMeterInterval(500.0);
        MaintenanceCompletionAnchor anchor = anchor(equipmentId, regulation.getId(),
                Instant.parse("2026-06-01T00:00:00Z"), 0.0);

        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.of(anchor));
        when(meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId))
                .thenReturn(java.util.List.of());

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.status()).isEqualTo(MaintenanceDueStatus.BLOCKED);
        assertThat(result.structuredExplanation().blockingCode()).isEqualTo("MISSING_ACTIVE_METER");
        assertThat(result.structuredExplanation().blockingField()).isEqualTo("ENGINE_HOURS");
        assertThat(result.explanation()).contains("Required active meter is missing: ENGINE_HOURS");
    }

    @Test
    void langParamReturnsLocalizedExplanationInsteadOfEnglish() {
        ReflectionTestUtils.setField(service, "clock",
                Clock.fixed(Instant.parse("2026-06-02T12:00:00Z"), ZoneOffset.UTC));
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setInitialSchedulePolicy(MaintenanceInitialSchedulePolicy.FROM_OPERATION_START);
        regulation.setPeriodicityUnit(PeriodicityUnit.DAY);
        regulation.setPeriodicityValue(1);
        equipment(equipmentId, LocalDate.parse("2026-05-01"));

        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.empty());

        MaintenanceDueCalculationDto ru = service.calculate(equipmentId, regulation, "ru");
        assertThat(ru.explanation()).isEqualTo("Календарный триггер просрочен (от даты ввода в эксплуатацию)");

        MaintenanceDueCalculationDto uz = service.calculate(equipmentId, regulation, "uz");
        assertThat(uz.explanation()).isEqualTo("Kalendar trigeri muddati o'tgan (ekspluatatsiyaga kiritilgan sanadan)");

        MaintenanceDueCalculationDto en = service.calculate(equipmentId, regulation, "en");
        assertThat(en.explanation()).isEqualTo("Calendar trigger overdue (from operation start date)");

        MaintenanceDueCalculationDto noLang = service.calculate(equipmentId, regulation);
        assertThat(noLang.explanation()).isEqualTo("Calendar trigger overdue from operation start");
    }

    @Test
    void langParamAcceptsAcceptLanguageHeaderStyleValueAndIgnoresUnsupportedLocale() {
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setTriggerPolicy(MaintenanceTriggerPolicy.MANUAL);

        // Accept-Language style value ("uz-UZ,uz;q=0.9,ru;q=0.8") - first supported tag wins.
        MaintenanceDueCalculationDto fromHeader = service.calculate(equipmentId, regulation, "uz-UZ,uz;q=0.9,ru;q=0.8");
        assertThat(fromHeader.explanation()).isEqualTo("Reglament bo'yicha qo'lda ishga tushirish");

        // Unsupported locale (no ru/uz/en tag anywhere) - normalizeLang() returns null, so the
        // untranslated legacy English text is returned rather than an error or empty response.
        MaintenanceDueCalculationDto unsupported = service.calculate(equipmentId, regulation, "fr-FR");
        assertThat(unsupported.explanation()).isEqualTo("Manual trigger policy");
    }

    private MaintenanceRegulation regulation(UUID equipmentId) {
        MaintenanceRegulation regulation = new MaintenanceRegulation();
        regulation.setId(UUID.randomUUID());
        regulation.setEquipmentTypeId(UUID.randomUUID());
        regulation.setPeriodicityUnit(PeriodicityUnit.MONTH);
        regulation.setPeriodicityValue(1);
        regulation.setTriggerPolicy(MaintenanceTriggerPolicy.ANY);
        regulation.setInitialSchedulePolicy(MaintenanceInitialSchedulePolicy.FROM_OPERATION_START);
        regulation.setCreatedAt(Instant.parse("2026-05-20T00:00:00Z"));
        return regulation;
    }

    private Equipment equipment(UUID equipmentId, LocalDate operationStartDate) {
        Equipment equipment = new Equipment();
        ReflectionTestUtils.setField(equipment, "id", equipmentId);
        equipment.setOperationStartDate(operationStartDate);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        return equipment;
    }

    private EquipmentMeter meter(UUID equipmentId, MeterType meterType, double currentValue) {
        EquipmentMeter meter = new EquipmentMeter();
        meter.setId(UUID.randomUUID());
        meter.setEquipmentId(equipmentId);
        meter.setMeterType(meterType);
        meter.setCurrentValue(currentValue);
        return meter;
    }

    private MaintenanceCompletionAnchor anchor(UUID equipmentId,
                                               UUID regulationId,
                                               Instant performedAt,
                                               double meterValue) {
        return meterAnchor(equipmentId, regulationId, performedAt, MeterType.ENGINE_HOURS, meterValue);
    }

    private MaintenanceCompletionAnchor meterAnchor(UUID equipmentId,
                                                    UUID regulationId,
                                                    Instant performedAt,
                                                    MeterType meterType,
                                                    double meterValue) {
        MaintenanceCompletionAnchor anchor = new MaintenanceCompletionAnchor();
        anchor.setEquipmentId(equipmentId);
        anchor.setRegulationId(regulationId);
        anchor.setPerformedAt(performedAt);
        anchor.setRecalculationPolicy(MaintenanceRecalculationPolicy.FROM_ACTUAL_COMPLETION);
        anchor.setMeterSnapshots("""
                [{"meterType":"%s","value":%s}]
                """.formatted(meterType.name(), meterValue));
        return anchor;
    }
}
