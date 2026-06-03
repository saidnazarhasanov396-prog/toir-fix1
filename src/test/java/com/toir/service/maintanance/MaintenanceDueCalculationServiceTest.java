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

        assertThat(result.status()).isEqualTo(MaintenanceDueStatus.DUE);
        assertThat(result.dueByCalendar()).isFalse();
        assertThat(result.dueByMeter()).isTrue();
        assertThat(result.meterType()).isEqualTo(MeterType.ENGINE_HOURS);
        assertThat(result.meterCurrentValue()).isEqualTo(520.0);
        assertThat(result.meterInterval()).isEqualTo(500.0);
        assertThat(result.meterAnchorValue()).isZero();
        assertThat(result.meterRemaining()).isZero();
        assertThat(result.explanation()).contains("Meter trigger due");
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

        assertThat(anyResult.status()).isEqualTo(MaintenanceDueStatus.DUE);
        assertThat(anyResult.dueByCalendar()).isFalse();
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
    void combinedAnyUsesMeterWhenMeterDueAndCalendarNotDue() {
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(equipmentId);
        regulation.setTriggerPolicy(MaintenanceTriggerPolicy.ANY);
        regulation.setTriggerMeterType(MeterType.ENGINE_HOURS);
        regulation.setTriggerMeterInterval(500.0);

        EquipmentMeter meter = meter(equipmentId, MeterType.ENGINE_HOURS, 520.0);
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

        EquipmentMeter meter = meter(equipmentId, MeterType.ENGINE_HOURS, 520.0);
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

        EquipmentMeter meter = meter(equipmentId, MeterType.ENGINE_HOURS, 520.0);
        MaintenanceCompletionAnchor anchor = anchor(equipmentId, regulation.getId(), Instant.now().minusSeconds(172800), 0.0);

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

        EquipmentMeter meter = meter(equipmentId, MeterType.ENGINE_HOURS, 520.0);

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

        EquipmentMeter meter = meter(equipmentId, MeterType.ENGINE_HOURS, 520.0);

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
        EquipmentMeter meter = meter(equipmentId, MeterType.ENGINE_HOURS, 520.0);

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
        EquipmentMeter meter = meter(equipmentId, MeterType.ENGINE_HOURS, 520.0);

        when(anchorRepository.findLatestAnchor(equipmentId, regulation.getId(), null)).thenReturn(Optional.empty());
        when(meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId)).thenReturn(java.util.List.of(meter));

        MaintenanceDueCalculationDto result = service.calculate(equipmentId, regulation);

        assertThat(result.status()).isEqualTo(MaintenanceDueStatus.DUE);
        assertThat(result.dueByMeter()).isTrue();
        assertThat(result.dueByCalendar()).isFalse();
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
        MaintenanceCompletionAnchor anchor = new MaintenanceCompletionAnchor();
        anchor.setEquipmentId(equipmentId);
        anchor.setRegulationId(regulationId);
        anchor.setPerformedAt(performedAt);
        anchor.setRecalculationPolicy(MaintenanceRecalculationPolicy.FROM_ACTUAL_COMPLETION);
        anchor.setMeterSnapshots("""
                [{"meterType":"ENGINE_HOURS","value":%s}]
                """.formatted(meterValue));
        return anchor;
    }
}
